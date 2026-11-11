package com.tenmilelabs.chefai.core.data.sync.network

import com.tenmilelabs.chefai.auth.data.network.isAuthenticatedOrigin
import com.tenmilelabs.chefai.auth.data.network.toOrigin
import io.ktor.client.plugins.api.createClientPlugin

/**
 * The sync protocol this client speaks. The backend answers `426 Upgrade Required` to `/sync/push`
 * and `/sync/pull` when it is below the server's minimum (backend `docs/sync-protocol.md`,
 * "Protocol Version and Upgrade Gate"). Raise it only together with a server change.
 */
const val SYNC_PROTOCOL_VERSION: Int = 1
const val HEADER_SYNC_PROTOCOL: String = "X-Sync-Protocol"
const val HEADER_CLIENT_VERSION: String = "X-Client-Version"

/**
 * Ktor client plugin that names this client's sync protocol and app version on every request to
 * the ChefAI backend. Scoped to [ClientVersionHeadersConfig.allowedBaseUrls] the same way
 * `AuthInterceptor` is, so third-party hosts never learn the app version.
 */
val ClientVersionHeaders = createClientPlugin("ClientVersionHeaders", ::ClientVersionHeadersConfig) {
    val allowedOrigins = pluginConfig.allowedBaseUrls.mapNotNull { it.toOrigin() }.toSet()
    val clientVersion = pluginConfig.clientVersionCode.toString()

    onRequest { request, _ ->
        if (!isAuthenticatedOrigin(request.url.buildString(), allowedOrigins)) return@onRequest
        request.headers[HEADER_SYNC_PROTOCOL] = SYNC_PROTOCOL_VERSION.toString()
        request.headers[HEADER_CLIENT_VERSION] = clientVersion
    }
}

class ClientVersionHeadersConfig {
    /** The ChefAI backend base URLs, as passed to `AuthInterceptor`. */
    var allowedBaseUrls: Collection<String> = emptyList()

    /** `BuildConfig.VERSION_CODE` in the app; a parameter so tests need no build config. */
    var clientVersionCode: Int = 0
}
