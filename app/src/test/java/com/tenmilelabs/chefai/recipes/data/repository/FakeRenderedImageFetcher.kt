package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.recipes.domain.repository.RenderedImageFetcher
import kotlin.time.Duration

/**
 * Stands in for the off-screen WebView so [com.tenmilelabs.chefai.recipes.domain.usecase.CacheRecipeImage]'s
 * tiering can be tested on plain JVM.
 */
class FakeRenderedImageFetcher(private val bytes: ByteArray? = null) : RenderedImageFetcher {

    var callCount: Int = 0
        private set
    var lastUrl: String? = null
        private set

    override suspend fun fetchImageBytes(imageUrl: String, budget: Duration): ByteArray? {
        callCount++
        lastUrl = imageUrl
        return bytes
    }
}
