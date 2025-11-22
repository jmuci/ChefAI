package com.tenmilelabs.chefai.core.di

import com.tenmilelabs.chefai.auth.data.network.AuthInterceptor
import com.tenmilelabs.chefai.recipes.data.network.ChefAIApiService
import com.tenmilelabs.chefai.recipes.data.network.RecipeNetworkDataSource
import com.tenmilelabs.chefai.auth.domain.SessionManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkDataSourceModule {

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
    fun provideHttpClient(sessionManager: SessionManager) = HttpClient(CIO) {
        expectSuccess = true

        // Install authentication interceptor
        install(AuthInterceptor) {
            this.sessionManager = sessionManager
        }

        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.HEADERS
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
}