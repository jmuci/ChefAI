package com.tenmilelabs.chefai.core.di

import com.tenmilelabs.chefai.auth.data.network.AuthInterceptor
import com.tenmilelabs.chefai.auth.domain.TokenProvider
import com.tenmilelabs.chefai.recipes.data.network.ChefAIApiService
import com.tenmilelabs.chefai.recipes.data.network.RecipeDetailApiService
import com.tenmilelabs.chefai.recipes.data.network.RecipeDetailNetworkDataSource
import com.tenmilelabs.chefai.recipes.data.network.RecipeNetworkDataSource
import com.tenmilelabs.chefai.search.data.network.RecipeSearchApiService
import com.tenmilelabs.chefai.search.data.network.RecipeSearchNetworkDataSource
import com.tenmilelabs.recipescraper.RecipeHtmlParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private const val SCRAPER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/128.0.0.0 Safari/537.36"

@Module
@InstallIn(SingletonComponent::class)
abstract class RecipeNetworkDataSourceModule {

    @Binds
    abstract fun bindRecipeNetworkDataSource(
        chefAIApiService: ChefAIApiService
    ): RecipeNetworkDataSource

    @Binds
    abstract fun bindRecipeSearchNetworkDataSource(
        recipeSearchApiService: RecipeSearchApiService
    ): RecipeSearchNetworkDataSource

    @Binds
    abstract fun bindRecipeDetailNetworkDataSource(
        recipeDetailApiService: RecipeDetailApiService
    ): RecipeDetailNetworkDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(tokenProvider: TokenProvider, json: Json) = HttpClient(CIO) {
        expectSuccess = true

        // Install authentication interceptor
        install(AuthInterceptor) {
            this.tokenProvider = tokenProvider
        }

        // No requestTimeoutMillis here deliberately: SyncApiService.pullRecipes pages ~100
        // recipes at a time and image upload/download stream bytes, both of which can
        // legitimately run longer than a search-appropriate timeout. Callers that need a tight
        // request timeout (e.g. search) set their own via a per-request `timeout { }` block,
        // which Ktor allows to override this client-level config.
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }

        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.HEADERS
        }

        install(ContentNegotiation) {
            json(json)
        }
    }

    /**
     * Unauthenticated client for fetching third-party recipe pages during URL import.
     * Must never carry ChefAI auth headers or run scraped HTML through content negotiation.
     *
     * Redirects are followed manually by callers (see [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter]
     * and [com.tenmilelabs.chefai.recipes.domain.usecase.CacheRecipeImage]) rather than by the
     * client, so every hop can be re-validated against the SSRF guard before it's followed — a
     * page can 3xx to an internal address just as easily as it can host one directly.
     */
    @Provides
    @Singleton
    @ScraperHttpClient
    fun provideScraperHttpClient(): HttpClient = HttpClient(CIO) {
        expectSuccess = true
        followRedirects = false

        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }

        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO
        }

        defaultRequest {
            header(HttpHeaders.UserAgent, SCRAPER_USER_AGENT)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
        }
    }

    /** Stateless — [RecipeHtmlParser] lives in the dependency-free `:recipe-scraper` module. */
    @Provides
    @Singleton
    fun provideRecipeHtmlParser(): RecipeHtmlParser = RecipeHtmlParser()
}