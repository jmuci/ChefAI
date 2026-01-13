package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.auth.domain.TokenProvider
import io.ktor.client.plugins.api.createClientPlugin
import timber.log.Timber

/**
 * Ktor client plugin that intercepts HTTP requests to add authentication headers.
 * Automatically includes the access token in the Authorization header if available.
 */
val AuthInterceptor = createClientPlugin("AuthInterceptor", ::AuthInterceptorConfig) {
    val tokenProvider = pluginConfig.tokenProvider

    onRequest { request, _ ->
        val accessToken = tokenProvider.getAccessToken()
        if (accessToken != null) {
            request.headers.append("Authorization", "Bearer $accessToken")
            Timber.d("Added auth token to request: ${request.url}")
        } else {
            Timber.d("No auth token available for request: ${request.url}")
        }
    }
}

/**
 * Configuration class for AuthInterceptor plugin.
 */
class AuthInterceptorConfig {
    lateinit var tokenProvider: TokenProvider
}
