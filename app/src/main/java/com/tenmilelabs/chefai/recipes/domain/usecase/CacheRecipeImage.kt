package com.tenmilelabs.chefai.recipes.domain.usecase

import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.core.di.ScraperHttpClient
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import com.tenmilelabs.chefai.recipes.data.network.BOT_WALL_STATUSES
import com.tenmilelabs.chefai.recipes.data.network.readImageBodyCapped
import com.tenmilelabs.chefai.recipes.data.repository.normalizeAndValidateUrl
import com.tenmilelabs.chefai.recipes.data.repository.validateRedirectTarget
import com.tenmilelabs.chefai.recipes.domain.repository.HostResolver
import com.tenmilelabs.chefai.recipes.domain.repository.RenderedImageFetcher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/** Overrides the scraper client's default HTML `Accept` for this one request. */
private const val IMAGE_ACCEPT_HEADER = "image/avif,image/webp,image/*,*/*;q=0.8"

/** Bounds manual redirect-following — mirrors [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter]. */
private const val MAX_REDIRECT_HOPS = 5

/**
 * Budget for the WebView tier — matches
 * [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter]'s `RENDER_BUDGET` for the
 * HTML fetch.
 */
private val IMAGE_RENDER_BUDGET = 8.seconds

/**
 * Downloads a recipe's hero image and caches it on-device.
 *
 * Mirrors [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter]'s two-tier ladder
 * one tier lower: a plain HTTP GET first, escalating to an off-screen WebView only when the response
 * looks like a bot wall refusing a non-browser client (see [BOT_WALL_STATUSES]). The same CDNs that
 * block the HTML fetch — Akamai on Food Network, Cloudflare on Serious Eats — block their own
 * images the same way; see ADR-010 Decision 6.
 *
 * Never fails the caller: any error, including a genuinely missing or unreachable image, returns
 * `null` rather than throwing. A recipe without a cached image is a normal outcome — the remote
 * `imageUrl` scraped into the draft is still there to fall back on — not a broken import, so every
 * failure path here is swallowed and logged rather than propagated.
 */
class CacheRecipeImage @Inject constructor(
    @ScraperHttpClient private val httpClient: HttpClient,
    private val renderedImageFetcher: RenderedImageFetcher,
    private val recipeImageStore: RecipeImageStore,
    private val hostResolver: HostResolver,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(recipeId: UUID, imageUrl: String): String? = withContext(ioDispatcher) {
        // The URL was scraped out of a third party's HTML, not typed by the user — the same SSRF
        // guard normalizeAndValidateUrl applies to a pasted page URL applies here too.
        val url = normalizeAndValidateUrl(imageUrl, hostResolver) ?: return@withContext null

        val bytes = fetchBytes(url) ?: return@withContext null
        recipeImageStore.write(recipeId, bytes)
    }

    private suspend fun fetchBytes(url: String): ByteArray? {
        val outcome = fetchOverHttp(url)
        return when (outcome) {
            is FetchOutcome.Bytes -> outcome.bytes
            is FetchOutcome.Failure -> {
                if (!outcome.looksLikeBotWall) return null
                Timber.i("HTTP image fetch fell short for %s, retrying with a rendered fetch", url)
                renderedImageFetcher.fetchImageBytes(url, IMAGE_RENDER_BUDGET)
            }
        }
    }

    /**
     * Follows redirects itself (the client has `followRedirects = false` — see
     * [com.tenmilelabs.chefai.core.di.NetworkModule.provideScraperHttpClient]) so every hop can be
     * re-validated by [validateRedirectTarget] before it's followed, not just the first URL.
     */
    private suspend fun fetchOverHttp(url: String): FetchOutcome {
        var currentUrl = url
        repeat(MAX_REDIRECT_HOPS + 1) {
            when (val step = fetchOnce(currentUrl)) {
                is FetchStep.Terminal -> return step.outcome
                is FetchStep.Redirect -> {
                    val next = validateRedirectTarget(currentUrl, step.location, hostResolver)
                    if (next == null) {
                        Timber.w("Recipe image redirect failed the SSRF guard: %s", step.location)
                        return FetchOutcome.Failure(looksLikeBotWall = false)
                    }
                    currentUrl = next
                }
            }
        }
        return FetchOutcome.Failure(looksLikeBotWall = false)
    }

    private suspend fun fetchOnce(url: String): FetchStep = try {
        val response = httpClient.get(url) {
            headers { set(HttpHeaders.Accept, IMAGE_ACCEPT_HEADER) }
        }
        val mimeType = response.contentType()?.withoutParameters()?.toString().orEmpty()
        val outcome = if (!mimeType.startsWith("image/")) {
            FetchOutcome.Failure(looksLikeBotWall = false)
        } else {
            val bytes = response.readImageBodyCapped()
            if (bytes != null) FetchOutcome.Bytes(bytes) else FetchOutcome.Failure(looksLikeBotWall = false)
        }
        FetchStep.Terminal(outcome)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RedirectResponseException) {
        val location = e.response.headers[HttpHeaders.Location]
        if (location != null) FetchStep.Redirect(location) else FetchStep.Terminal(FetchOutcome.Failure(looksLikeBotWall = false))
    } catch (e: ResponseException) {
        Timber.w(e, "Recipe image fetch received an error response")
        FetchStep.Terminal(FetchOutcome.Failure(looksLikeBotWall = e.response.status in BOT_WALL_STATUSES))
    } catch (e: Exception) {
        // Deliberately broader than DefaultRecipeImporter.fetchHtml's catch list: unlike the HTML
        // fetch (whose caller branches on a sealed RecipeImportResult), nothing upstream of this use
        // case handles a thrown exception — SaveImportedDraft has no try/catch around it — so an
        // uncaught type here would crash the import instead of just leaving it imageless.
        Timber.w(e, "Recipe image fetch failed for %s", url)
        FetchStep.Terminal(FetchOutcome.Failure(looksLikeBotWall = false))
    }

    private sealed interface FetchOutcome {
        data class Bytes(val bytes: ByteArray) : FetchOutcome
        data class Failure(val looksLikeBotWall: Boolean) : FetchOutcome
    }

    /** One step of [fetchOverHttp]'s redirect loop: either a terminal [outcome], or one more [location] to follow. */
    private sealed interface FetchStep {
        data class Terminal(val outcome: FetchOutcome) : FetchStep
        data class Redirect(val location: String) : FetchStep
    }
}
