package com.tenmilelabs.chefai.recipes.domain.repository

import kotlin.time.Duration

/**
 * Loads a page in a browser engine and hands back its rendered DOM.
 *
 * The plain HTTP fetch in [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporter] is
 * the fast path and handles the large majority of recipe sites. This exists for the ones that
 * refuse it — bot protection that scores TLS and JS fingerprints, or markup only assembled once
 * the page's own scripts have run.
 */
interface RenderedHtmlFetcher {

    /**
     * Loads [url], polling the rendered DOM until [accept] returns `true` or [budget] elapses.
     *
     * @param accept called with each DOM snapshot; returning `true` stops the load and yields that
     *   snapshot. Must be cheap and must not throw.
     * @return the accepted HTML, or `null` if the budget ran out without one.
     */
    suspend fun fetchRenderedHtml(url: String, budget: Duration, accept: (String) -> Boolean): String?
}
