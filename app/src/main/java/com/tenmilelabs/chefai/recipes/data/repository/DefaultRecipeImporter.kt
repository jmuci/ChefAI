package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.core.di.ScraperHttpClient
import com.tenmilelabs.chefai.core.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraft
import com.tenmilelabs.chefai.recipes.data.network.decodeHtml
import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult
import com.tenmilelabs.chefai.recipes.domain.repository.RecipeImporter
import com.tenmilelabs.chefai.recipes.domain.repository.RenderedHtmlFetcher
import com.tenmilelabs.recipescraper.RecipeHtmlParser
import com.tenmilelabs.recipescraper.model.ScrapeResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import timber.log.Timber
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/** Caps how much of a fetched page is read into memory — well past any real recipe page. */
private const val MAX_BODY_BYTES = 3 * 1024 * 1024

/**
 * How long the off-screen browser gets to produce a recipe before the import gives up on it.
 *
 * Long enough for a bot check that resolves itself plus a heavy page load, short enough that a URL
 * which simply isn't a recipe doesn't leave the user watching a spinner. Tune from real use.
 */
private val RENDER_BUDGET = 8.seconds

/**
 * Statuses a bot manager returns to a client it doesn't like. Akamai and Cloudflare both answer
 * 403; 401, 429 and 503 cover the rate-limit and "under attack mode" variants. A 404 is
 * deliberately absent — that page really is missing, and a browser won't find it either.
 */
private val BOT_WALL_STATUSES = setOf(
    HttpStatusCode.Unauthorized,
    HttpStatusCode.Forbidden,
    HttpStatusCode.TooManyRequests,
    HttpStatusCode.ServiceUnavailable,
)

@Singleton
class DefaultRecipeImporter @Inject constructor(
    @ScraperHttpClient private val httpClient: HttpClient,
    private val recipeHtmlParser: RecipeHtmlParser,
    private val renderedHtmlFetcher: RenderedHtmlFetcher,
    private val metadataRepository: MetadataRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RecipeImporter {

    override suspend fun import(url: String): RecipeImportResult = withContext(ioDispatcher) {
        val normalizedUrl = normalizeAndValidateUrl(url) ?: return@withContext RecipeImportResult.InvalidUrl

        val fetched = fetchHtml(normalizedUrl)
        val httpResult = when (fetched) {
            is FetchOutcome.Failure -> fetched.error
            is FetchOutcome.Html -> importFromHtml(fetched.body, normalizedUrl)
        }

        // A plain GET is enough for the large majority of recipe sites. Exactly two shapes of
        // failure are worth a second attempt in a real browser engine:
        //  - the site refused a non-browser client outright (a bot manager's 403);
        //  - it served a shell whose recipe markup only exists once its own scripts have run.
        // Anything else — a 404, a DNS failure, a timeout — would fail the same way in a browser,
        // several seconds later, so it is returned as-is.
        val refused = fetched is FetchOutcome.Failure && fetched.looksLikeBotWall
        if (!refused && httpResult !is RecipeImportResult.NoRecipeFound) return@withContext httpResult

        Timber.i("HTTP import fell short for %s, retrying with a rendered fetch", normalizedUrl)
        val rendered = renderedHtmlFetcher.fetchRenderedHtml(normalizedUrl, RENDER_BUDGET) { html ->
            recipeHtmlParser.parse(html, normalizedUrl) is ScrapeResult.Success
        }

        when {
            rendered != null -> importFromHtml(rendered, normalizedUrl)
            // Out of automated options. Only a browser the user can interact with clears an
            // interactive check — but a page that simply has no recipe on it stays that way, so
            // there is nothing to send them to.
            refused -> RecipeImportResult.NeedsBrowser(normalizedUrl)
            else -> httpResult
        }
    }

    override suspend fun importFromHtml(html: String, url: String): RecipeImportResult =
        withContext(ioDispatcher) {
            when (val scrapeResult = recipeHtmlParser.parse(html, url)) {
                is ScrapeResult.Success -> {
                    val knownIngredients = metadataRepository.observeAllIngredients().first()
                    val knownTags = metadataRepository.observeAllTags().first()
                    val draft = scrapeResult.recipe.toRecipeDraft(
                        recipeId = UuidV7Generator.newId(),
                        knownIngredients = knownIngredients,
                        knownTags = knownTags,
                    )
                    RecipeImportResult.Success(draft)
                }

                ScrapeResult.NoRecipeFound -> RecipeImportResult.NoRecipeFound
                is ScrapeResult.ParseError -> RecipeImportResult.ParseError(scrapeResult.message)
            }
        }

    private sealed interface FetchOutcome {
        data class Html(val body: String) : FetchOutcome

        /**
         * @property looksLikeBotWall the server answered, and answered with a refusal rather than
         *   a "no such page" — the signature of bot protection turning away a non-browser client.
         */
        data class Failure(
            val error: RecipeImportResult.NetworkError,
            val looksLikeBotWall: Boolean = false,
        ) : FetchOutcome
    }

    private suspend fun fetchHtml(url: String): FetchOutcome = try {
        val response = httpClient.get(url)
        val contentType = response.contentType()
        val mimeType = contentType?.withoutParameters()?.toString().orEmpty()
        if (!mimeType.equals("text/html", ignoreCase = true) &&
            !mimeType.equals("application/xhtml+xml", ignoreCase = true)
        ) {
            FetchOutcome.Failure(RecipeImportResult.NetworkError("Not an HTML page"))
        } else {
            FetchOutcome.Html(response.readBodyCapped(contentType?.charset()))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        Timber.w(e, "Recipe import request timed out")
        FetchOutcome.Failure(RecipeImportResult.NetworkError(e.message ?: "Request timed out"))
    } catch (e: ResponseException) {
        Timber.w(e, "Recipe import received an error response")
        FetchOutcome.Failure(
            error = RecipeImportResult.NetworkError(e.message ?: "Request failed"),
            looksLikeBotWall = e.response.status in BOT_WALL_STATUSES,
        )
    } catch (e: IOException) {
        Timber.w(e, "Recipe import network error")
        FetchOutcome.Failure(RecipeImportResult.NetworkError(e.message ?: "Network error"))
    }

    /**
     * Reads at most [MAX_BODY_BYTES] of the response body, protecting against a hostile/huge page,
     * and decodes it using [responseCharset] when the server declared one — see [decodeHtml].
     */
    private suspend fun HttpResponse.readBodyCapped(responseCharset: Charset?): String {
        val channel = bodyAsChannel()
        val buffer = ByteArray(MAX_BODY_BYTES)
        var offset = 0
        while (offset < buffer.size) {
            val read = channel.readAvailable(buffer, offset, buffer.size - offset)
            if (read == -1) break
            offset += read
        }
        return decodeHtml(buffer, offset, responseCharset)
    }
}
