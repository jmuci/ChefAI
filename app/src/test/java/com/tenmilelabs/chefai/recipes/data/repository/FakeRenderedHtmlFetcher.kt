package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.recipes.domain.repository.RenderedHtmlFetcher
import kotlin.time.Duration

/**
 * Stands in for the off-screen WebView so the importer's tiering can be tested on plain JVM.
 *
 * [html] is offered to the caller's `accept` predicate exactly as the real fetcher offers a DOM
 * snapshot, so a fixture that doesn't parse exercises the give-up path faithfully.
 */
class FakeRenderedHtmlFetcher(private val html: String? = null) : RenderedHtmlFetcher {

    var callCount: Int = 0
        private set
    var lastUrl: String? = null
        private set

    override suspend fun fetchRenderedHtml(
        url: String,
        budget: Duration,
        accept: (String) -> Boolean,
    ): String? {
        callCount++
        lastUrl = url
        return html?.takeIf(accept)
    }
}
