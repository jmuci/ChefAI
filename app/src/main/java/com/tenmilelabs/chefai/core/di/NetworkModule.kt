package com.tenmilelabs.chefai.core.di

import com.tenmilelabs.chefai.auth.data.network.AuthInterceptor
import com.tenmilelabs.chefai.auth.domain.TokenProvider
import com.tenmilelabs.chefai.recipes.data.network.ChefAIApiService
import com.tenmilelabs.chefai.recipes.data.network.RecipeNetworkDataSource
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
     */
    @Provides
    @Singleton
    @ScraperHttpClient
    fun provideScraperHttpClient(): HttpClient = HttpClient(CIO) {
        expectSuccess = true
        followRedirects = true

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
}