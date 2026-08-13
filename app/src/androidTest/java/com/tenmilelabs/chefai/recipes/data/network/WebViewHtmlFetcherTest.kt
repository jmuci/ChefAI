package com.tenmilelabs.chefai.recipes.data.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tenmilelabs.recipescraper.RecipeHtmlParser
import com.tenmilelabs.recipescraper.model.ScrapeResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises the off-screen WebView against `data:` URLs, so the polling, extraction and give-up
 * paths are covered without touching the network or depending on any real site's markup.
 */
@RunWith(AndroidJUnit4::class)
class WebViewHtmlFetcherTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fetcher = WebViewHtmlFetcher(context)
    private val parser = RecipeHtmlParser()

    private fun dataUrl(html: String): String =
        "data:text/html;charset=utf-8," + java.net.URLEncoder.encode(html, "UTF-8").replace("+", "%20")

    @Test
    fun returnsTheRenderedDomOnceItSatisfiesTheParser() = runTest {
        val html = """
            <html><head>
            <script type="application/ld+json">
            {"@type":"Recipe","name":"Rendered Recipe",
             "recipeIngredient":["2 cups flour"],"recipeInstructions":["Mix."]}
            </script>
            </head><body><h1>Rendered Recipe</h1></body></html>
        """.trimIndent()

        val result = fetcher.fetchRenderedHtml(dataUrl(html), budget = 10.seconds) { snapshot ->
            parser.parse(snapshot, "https://example.com/recipe") is ScrapeResult.Success
        }

        assertNotNull(result)
        assertTrue("expected the rendered DOM, got: $result", result!!.contains("Rendered Recipe"))
    }

    @Test
    fun picksUpMarkupThatOnlyExistsAfterThePagesScriptsRun() = runTest {
        // The case a plain HTTP fetch cannot handle: the shell carries no recipe at all.
        val html = """
            <html><head><script>
              document.addEventListener('DOMContentLoaded', function () {
                var s = document.createElement('script');
                s.type = 'application/ld+json';
                s.textContent = JSON.stringify({
                  '@type': 'Recipe', name: 'Late Recipe',
                  recipeIngredient: ['1 cup sugar'], recipeInstructions: ['Stir.']
                });
                document.head.appendChild(s);
              });
            </script></head><body></body></html>
        """.trimIndent()

        val result = fetcher.fetchRenderedHtml(dataUrl(html), budget = 10.seconds) { snapshot ->
            parser.parse(snapshot, "https://example.com/recipe") is ScrapeResult.Success
        }

        assertNotNull(result)
        assertTrue("expected the injected markup, got: $result", result!!.contains("Late Recipe"))
    }

    @Test
    fun returnsNullWhenTheBudgetRunsOutWithoutARecipe() = runTest {
        val html = "<html><body><h1>Just a blog post</h1></body></html>"

        val result = fetcher.fetchRenderedHtml(dataUrl(html), budget = 2.seconds) { snapshot ->
            parser.parse(snapshot, "https://example.com/blog") is ScrapeResult.Success
        }

        assertNull(result)
    }
}
