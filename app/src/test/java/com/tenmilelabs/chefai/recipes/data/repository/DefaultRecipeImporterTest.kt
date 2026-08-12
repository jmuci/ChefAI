package com.tenmilelabs.chefai.recipes.data.repository

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.repository.FakeMetadataRepository
import com.tenmilelabs.chefai.core.testutil.testIngredients
import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult
import com.tenmilelabs.recipescraper.RecipeHtmlParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val JSON_LD_RECIPE_HTML = """
    <html><head>
    <script type="application/ld+json">
    {
      "@type": "Recipe",
      "name": "Mock Recipe",
      "recipeIngredient": ["2 cups flour", "1 cup sugar"],
      "recipeInstructions": ["Mix.", "Bake."]
    }
    </script>
    </head><body></body></html>
"""

private const val NO_RECIPE_HTML = "<html><body><h1>Just a blog post</h1></body></html>"

class DefaultRecipeImporterTest {

    private fun importer(engine: MockEngine): DefaultRecipeImporter = DefaultRecipeImporter(
        httpClient = HttpClient(engine) { expectSuccess = true },
        recipeHtmlParser = RecipeHtmlParser(),
        metadataRepository = FakeMetadataRepository(),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun htmlEngine(html: String, contentType: String = "text/html") = MockEngine { _ ->
        respond(
            content = ByteReadChannel(html),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, contentType),
        )
    }

    @Test
    fun `imports a JSON-LD recipe and maps ingredients against the known catalog`() = runTest {
        val result = importer(htmlEngine(JSON_LD_RECIPE_HTML)).import("https://example.com/recipe")

        val success = result as? RecipeImportResult.Success
        assertThat(success).isNotNull()
        assertThat(success!!.draft.title).isEqualTo("Mock Recipe")
        val flourIngredient = success.draft.ingredients.find { it.ingredientDisplayName.equals("flour", ignoreCase = true) }
        assertThat(flourIngredient).isNotNull()
        assertThat(flourIngredient!!.ingredientId).isEqualTo(testIngredients.first { it.displayName == "Flour" }.uuid)
    }

    @Test
    fun `returns NoRecipeFound when the page has no recipe markup`() = runTest {
        val result = importer(htmlEngine(NO_RECIPE_HTML)).import("https://example.com/blog")

        assertThat(result).isEqualTo(RecipeImportResult.NoRecipeFound)
    }

    @Test
    fun `returns NetworkError on a 404`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }

        val result = importer(engine).import("https://example.com/missing")

        assertThat(result).isInstanceOf(RecipeImportResult.NetworkError::class.java)
    }

    @Test
    fun `returns NetworkError when the content type is not html`() = runTest {
        val result = importer(htmlEngine(JSON_LD_RECIPE_HTML, contentType = "application/pdf"))
            .import("https://example.com/file.pdf")

        assertThat(result).isInstanceOf(RecipeImportResult.NetworkError::class.java)
    }

    @Test
    fun `returns InvalidUrl for text that isn't a url`() = runTest {
        val result = importer(htmlEngine(JSON_LD_RECIPE_HTML)).import("not a url")

        assertThat(result).isEqualTo(RecipeImportResult.InvalidUrl)
    }

    @Test
    fun `returns InvalidUrl for a localhost address`() = runTest {
        val result = importer(htmlEngine(JSON_LD_RECIPE_HTML)).import("http://localhost:8080/x")

        assertThat(result).isEqualTo(RecipeImportResult.InvalidUrl)
    }

    @Test
    fun `returns InvalidUrl for a private-network address`() = runTest {
        val result = importer(htmlEngine(JSON_LD_RECIPE_HTML)).import("http://192.168.1.5/x")

        assertThat(result).isEqualTo(RecipeImportResult.InvalidUrl)
    }
}
