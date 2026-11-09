package com.tenmilelabs.recipescraper.jsonld

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * JVM-only because it needs threads with chosen stack sizes: whether the uncapped recursion
 * overflowed used to depend on stack size and JIT state (issue 339), so the parse runs on a roomy
 * stack and the mapping on a deliberately small one, which makes the overflow deterministic.
 */
class JsonLdDeepNestingTest {

    @Test
    fun `mapping a recipe with every array-valued field nested 6000 deep does not overflow — issue 339`() {
        // Given a Recipe whose image, author, yield, category, cuisine, keywords and language are each
        // wrapped in 6000 arrays, parsed where the JSON parser itself has stack to spare
        fun deep(value: String): String = "[".repeat(NESTING) + value + "]".repeat(NESTING)
        val json = """
            {
              "@type": "Recipe",
              "name": "Deep Pancakes",
              "recipeIngredient": ["1 cup flour"],
              "image": ${deep("\"https://example.com/p.jpg\"")},
              "author": ${deep("\"Jane\"")},
              "recipeYield": ${deep("\"4\"")},
              "recipeCategory": ${deep("\"Breakfast\"")},
              "recipeCuisine": ${deep("\"French\"")},
              "keywords": ${deep("\"easy\"")},
              "inLanguage": ${deep("\"en\"")}
            }
        """.trimIndent()
        val recipeObject = onThread(stackBytes = 256L * 1024 * 1024) { Json.parseToJsonElement(json).jsonObject }

        // When it is mapped on a small stack
        val recipe = onThread(stackBytes = SMALL_STACK) { mapJsonLdRecipe(recipeObject, "https://example.com/r") }

        // Then nothing overflowed, and the over-nested values are simply left out
        assertNotNull(recipe)
        assertEquals("Deep Pancakes", recipe.title)
        assertNull(recipe.imageUrl)
        assertNull(recipe.author)
        assertNull(recipe.servings)
        assertNull(recipe.language)
        assertEquals(emptyList(), recipe.keywords)
    }

    /** Runs [block] on a fresh thread with [stackBytes] of stack, rethrowing whatever it threw. */
    private fun <T> onThread(stackBytes: Long, block: () -> T): T {
        var result: Result<T>? = null
        val thread = Thread(null, { result = runCatching(block) }, "deep-nesting", stackBytes)
        thread.start()
        thread.join()
        return requireNotNull(result) { "thread produced no result" }.getOrThrow()
    }

    private companion object {
        const val NESTING = 6_000
        const val SMALL_STACK = 256L * 1024
    }
}
