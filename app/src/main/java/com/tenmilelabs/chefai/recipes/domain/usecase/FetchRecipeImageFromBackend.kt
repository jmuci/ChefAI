package com.tenmilelabs.chefai.recipes.domain.usecase

import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import com.tenmilelabs.chefai.recipes.data.network.readImageBodyCapped
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fetches a recipe's image from ChefAI's own backend and caches it on this device.
 *
 * This is the tier that makes a user's own photo appear on a second device at all. It is also the
 * cheap path for a scraped image whose CDN blocks plain HTTP clients: rather than every device
 * separately driving an off-screen `WebView` past Akamai, the one device that already got through
 * uploaded the bytes, and the rest simply download them.
 *
 * Note what this deliberately is *not*: a change to what the image loader fetches. The bytes land in
 * [RecipeImageStore] and the UI keeps rendering from `localImagePath`, exactly as it does for a
 * scraped image. Pointing Coil at an authenticated endpoint instead would mean teaching its OkHttp
 * stack to attach the JWT and then scoping that by host so the token never reaches the third-party
 * CDNs the app also hotlinks — a token-leak surface that ADR-010 created `@ScraperHttpClient`
 * specifically to avoid, and there is no reason to reintroduce it here.
 *
 * Uses the default authenticated [HttpClient] and builds its URL only from
 * [BuildConfig.API_BASE_URL]; it never touches a recipe's `imageUrl`, so it cannot be pointed at a
 * third-party host by data.
 *
 * Never throws — a failure just leaves the recipe on the hotlink fallback, as before.
 */
class FetchRecipeImageFromBackend @Inject constructor(
    private val client: HttpClient,
    private val recipeImageStore: RecipeImageStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** @return the stored file's absolute path, or `null` if the image could not be fetched. */
    suspend operator fun invoke(recipeId: UUID): String? = withContext(ioDispatcher) {
        try {
            val response = client.get("${BuildConfig.API_BASE_URL}/recipes/$recipeId/image") {
                expectSuccess = false
            }

            if (!response.status.isSuccess()) {
                Timber.d("Backend has no image for %s (%s)", recipeId, response.status)
                return@withContext null
            }

            val mimeType = response.contentType()?.withoutParameters()?.toString().orEmpty()
            if (!mimeType.startsWith("image/")) {
                Timber.w("Backend returned %s for %s, not an image", mimeType, recipeId)
                return@withContext null
            }

            val bytes = response.readImageBodyCapped() ?: return@withContext null
            recipeImageStore.write(recipeId, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch backend image for %s", recipeId)
            null
        }
    }
}
