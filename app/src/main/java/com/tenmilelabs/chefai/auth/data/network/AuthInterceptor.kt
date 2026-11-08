package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.auth.domain.TokenProvider
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import timber.log.Timber

/**
 * Ktor client plugin that adds `Authorization: Bearer <token>` to requests bound for the ChefAI
 * backend, when a token is available.
 *
 * Only requests whose origin (scheme, host and port) matches one of
 * [AuthInterceptorConfig.allowedBaseUrls] get the header, so the JWT can never reach a third-party
 * host even if the default client is pointed at one.
 */
val AuthInterceptor = createClientPlugin("AuthInterceptor", ::AuthInterceptorConfig) {
    val tokenProvider = pluginConfig.tokenProvider
    val allowedOrigins = pluginConfig.allowedBaseUrls.mapNotNull { it.toOrigin() }.toSet()

    onRequest { request, _ ->
        val url = request.url.buildString()
        if (!isAuthenticatedOrigin(url, allowedOrigins)) {
            Timber.d("Not adding auth token to non-ChefAI host: ${request.url.host}")
            return@onRequest
        }
        val accessToken = tokenProvider.getAccessToken()
        if (accessToken != null) {
            request.headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
            Timber.d("Added auth token to request: $url")
        } else {
            Timber.d("No auth token available for request: $url")
        }
    }
}

/**
 * Configuration class for AuthInterceptor plugin.
 */
class AuthInterceptorConfig {
    lateinit var tokenProvider: TokenProvider

    /**
     * The ChefAI backend base URLs the token may be sent to. A debug build passes both the local
     * and the remote base, since `ApiEnvironmentInterceptor` may rewrite one into the other.
     */
    var allowedBaseUrls: Collection<String> = emptyList()
}

/** A URL's scheme, host and port — the parts that decide where a request actually goes. */
internal data class Origin(val scheme: String, val host: String, val port: Int)

internal fun String.toOrigin(): Origin? = runCatching {
    val url = Url(this)
    Origin(url.protocol.name, url.host.lowercase(), url.port)
}.getOrNull()?.takeIf { it.host.isNotEmpty() }

/** Whether [url] is bound for one of [allowedOrigins]. Unparseable URLs are never authenticated. */
internal fun isAuthenticatedOrigin(url: String, allowedOrigins: Set<Origin>): Boolean =
    url.toOrigin()?.let { it in allowedOrigins } ?: false
