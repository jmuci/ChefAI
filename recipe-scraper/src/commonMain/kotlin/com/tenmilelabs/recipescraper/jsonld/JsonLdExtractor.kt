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
 * A malformed block is skipped rather than failing the whole extraction.
 */
internal fun extractJsonLdRecipe(document: Document, sourceUrl: String): ScrapedRecipe? {
    for (script in document.select("script[type=application/ld+json]")) {
        val root = runCatching { LENIENT_JSON.parseToJsonElement(script.data()) }.getOrNull() ?: continue
        for (candidate in findRecipeCandidates(root)) {
            val recipe = mapJsonLdRecipe(candidate, sourceUrl)
            if (recipe != null) return recipe
        }
    }
    return null
}

private val LENIENT_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Walks a parsed JSON-LD document looking for `Recipe`-typed objects, in document order. Handles a
 * bare object, a top-level array, an `@graph` wrapper, and an `@graph` nested inside an array
 * element.
 */
private fun findRecipeCandidates(element: JsonElement): List<JsonObject> = when (element) {
    is JsonObject -> buildList {
        if (element.isRecipeType()) add(element)
        (element["@graph"] as? JsonArray)?.let { addAll(findRecipeCandidates(it)) }
    }

    is JsonArray -> element.flatMap { findRecipeCandidates(it) }
    else -> emptyList()
}

private fun JsonObject.isRecipeType(): Boolean = typeNames().any { it.equals("Recipe", ignoreCase = true) }

/** `@type` as either a bare string or an array of strings. */
internal fun JsonObject.typeNames(): List<String> = when (val type = this["@type"]) {
    is JsonPrimitive -> listOfNotNull(type.contentOrNull)
    is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    else -> emptyList()
}
