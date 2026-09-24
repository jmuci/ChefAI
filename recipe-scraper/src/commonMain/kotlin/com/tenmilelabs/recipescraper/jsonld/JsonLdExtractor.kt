package com.tenmilelabs.recipescraper.jsonld

import com.fleeksoft.ksoup.nodes.Document
import com.tenmilelabs.recipescraper.model.ScrapedRecipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Finds and maps the first usable schema.org `Recipe` in a document's `application/ld+json`
 * blocks.
 *
 * A "candidate miss" — a `Recipe`-typed object with no usable `name` — does not abort the search;
 * the next candidate (in the same block's `@graph`, or a later script block) is tried instead.
 * The first candidate that also has ingredients wins; a name-only stub that appears before the
 * real recipe is returned only if nothing better turns up. A malformed block is skipped rather than
 * failing the whole extraction.
 */
internal fun extractJsonLdRecipe(document: Document, sourceUrl: String): ScrapedRecipe? {
    var fallback: ScrapedRecipe? = null
    for (script in document.select("script[type=application/ld+json]")) {
        val root = runCatching { LENIENT_JSON.parseToJsonElement(script.data()) }.getOrNull() ?: continue
        for (candidate in findRecipeCandidates(root)) {
            val recipe = mapJsonLdRecipe(candidate, sourceUrl) ?: continue
            if (recipe.ingredients.isNotEmpty()) return recipe
            if (fallback == null) fallback = recipe
        }
    }
    return fallback
}

private val LENIENT_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Walks a parsed JSON-LD document looking for `Recipe`-typed objects, in document order. Handles a
 * bare object, a top-level array, an `@graph` wrapper, an `@graph` nested inside an array element,
 * and a recipe hung off a `WebPage`'s `mainEntity`. Depth-capped like the mapper's walkers.
 */
private fun findRecipeCandidates(element: JsonElement, depth: Int = 0): List<JsonObject> =
    if (depth >= MAX_NESTING_DEPTH) emptyList() else when (element) {
        is JsonObject -> buildList {
            if (element.isRecipeType()) add(element)
            (element["@graph"] as? JsonArray)?.let { addAll(findRecipeCandidates(it, depth + 1)) }
            element["mainEntity"]?.let { addAll(findRecipeCandidates(it, depth + 1)) }
        }

        is JsonArray -> element.flatMap { findRecipeCandidates(it, depth + 1) }
        else -> emptyList()
    }

private fun JsonObject.isRecipeType(): Boolean = typeNames().any { it.equals("Recipe", ignoreCase = true) }

/** `@type` as either a bare string or an array of strings. */
internal fun JsonObject.typeNames(): List<String> = when (val type = this["@type"]) {
    is JsonPrimitive -> listOfNotNull(type.contentOrNull)
    is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    else -> emptyList()
}
