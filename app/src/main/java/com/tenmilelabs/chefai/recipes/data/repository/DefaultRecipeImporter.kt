package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.core.di.ScraperHttpClient
import com.tenmilelabs.chefai.core.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraft
import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult
import com.tenmilelabs.chefai.recipes.domain.repository.RecipeImporter
import com.tenmilelabs.recipescraper.RecipeHtmlParser
import com.tenmilelabs.recipescraper.model.ScrapeResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Caps how much of a fetched page is read into memory — well past any real recipe page. */
private const val MAX_BODY_BYTES = 3 * 1024 * 1024

@Singleton
class DefaultRecipeImporter @Inject constructor(
    @ScraperHttpClient private val httpClient: HttpClient,
    private val recipeHtmlParser: RecipeHtmlParser,
    private val metadataRepository: MetadataRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RecipeImporter {

    override suspend fun import(url: String): RecipeImportResult = withContext(ioDispatcher) {
        val normalizedUrl = normalizeAndValidateUrl(url) ?: return@withContext RecipeImportResult.InvalidUrl

        val html = fetchHtml(normalizedUrl).let { result ->
            if (result is FetchOutcome.Failure) return@withContext result.error
            (result as FetchOutcome.Html).body
        }

        when (val scrapeResult = recipeHtmlParser.parse(html, normalizedUrl)) {
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
        data class Failure(val error: RecipeImportResult.NetworkError) : FetchOutcome
    }

    private suspend fun fetchHtml(url: String): FetchOutcome = try {
        val response = httpClient.get(url)
        val contentType = response.headers[HttpHeaders.ContentType].orEmpty()
        if (!contentType.contains("text/html", ignoreCase = true) &&
            !contentType.contains("application/xhtml+xml", ignoreCase = true)
        ) {
            FetchOutcome.Failure(RecipeImportResult.NetworkError("Not an HTML page"))
        } else {
            FetchOutcome.Html(response.readBodyCapped())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        Timber.w(e, "Recipe import request timed out")
        FetchOutcome.Failure(RecipeImportResult.NetworkError(e.message ?: "Request timed out"))
    } catch (e: ResponseException) {
        Timber.w(e, "Recipe import received an error response")
        FetchOutcome.Failure(RecipeImportResult.NetworkError(e.message ?: "Request failed"))
    } catch (e: IOException) {
        Timber.w(e, "Recipe import network error")
        FetchOutcome.Failure(RecipeImportResult.NetworkError(e.message ?: "Network error"))
    }

    /** Reads at most [MAX_BODY_BYTES] of the response body, protecting against a hostile/huge page. */
    private suspend fun HttpResponse.readBodyCapped(): String {
        val channel = bodyAsChannel()
        val buffer = ByteArray(MAX_BODY_BYTES)
        var offset = 0
        while (offset < buffer.size) {
            val read = channel.readAvailable(buffer, offset, buffer.size - offset)
            if (read == -1) break
            offset += read
        }
        return buffer.decodeToString(0, offset)
    }
}
