package com.tenmilelabs.chefai.recipes.domain.repository

import kotlin.time.Duration

/**
 * Downloads an image through a browser engine, for CDNs that 403 a plain HTTP client.
 *
 * Sibling to [RenderedHtmlFetcher]: the same TLS/IP fingerprinting that blocks the Ktor client's
 * page fetch (see [RenderedHtmlFetcher]'s KDoc) blocks its image fetches too, on exactly the sites
 * that motivated that fallback. Headers cannot fix this — only a real browser engine gets through.
 */
interface RenderedImageFetcher {

    /**
     * Loads [imageUrl] and returns its decoded bytes, or `null` if [budget] elapses first or the
     * load fails.
     */
    suspend fun fetchImageBytes(imageUrl: String, budget: Duration): ByteArray?
}
