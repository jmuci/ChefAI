package com.tenmilelabs.recipescraper.jsonld

import com.tenmilelabs.recipescraper.ingredient.parseIngredient
import com.tenmilelabs.recipescraper.model.ScrapedIngredient
import com.tenmilelabs.recipescraper.model.ScrapedRecipe
import com.tenmilelabs.recipescraper.util.cleanHtmlText
import com.tenmilelabs.recipescraper.util.firstIntOrNull
import com.tenmilelabs.recipescraper.util.firstNutritionIntOrNull
import com.tenmilelabs.recipescraper.util.parseDurationToMinutes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Per-serving plausibility bounds for values pulled out of a page's `nutrition` block — generous
// enough to admit any real recipe, tight enough to reject an obviously mis-extracted number (a
// percentage, a year, an unrelated id).
private const val MAX_PLAUSIBLE_CALORIES = 5000
private const val MAX_PLAUSIBLE_PROTEIN_GRAMS = 300

/**
 * Maps one `Recipe`-typed JSON-LD object to a [ScrapedRecipe].
 *
 * Returns `null` when the object has no usable `name` — per schema.org's own spec `name` is
 * required, so an object without one is not a real recipe listing, just a malformed or partial one.
 */
internal fun mapJsonLdRecipe(obj: JsonObject, sourceUrl: String): ScrapedRecipe? {
    val title = cleanHtmlText(obj.stringOrNull("name")) ?: return null
    val nutrition = obj["nutrition"] as? JsonObject

    return ScrapedRecipe(
        title = title,
        description = cleanHtmlText(obj.stringOrNull("description")),
        imageUrl = obj["image"]?.firstImageUrlOrNull(),
        prepTimeMinutes = parseDurationToMinutes(obj.stringOrNull("prepTime")),
        cookTimeMinutes = parseDurationToMinutes(obj.stringOrNull("cookTime")),
        totalTimeMinutes = parseDurationToMinutes(obj.stringOrNull("totalTime")),
        servings = obj["recipeYield"]?.yieldStrings()?.firstNotNullOfOrNull(::firstIntOrNull),
        caloriesPerServing = nutrition?.stringOrNull("calories")
            ?.let { firstNutritionIntOrNull(it, MAX_PLAUSIBLE_CALORIES) },
        proteinGramsPerServing = nutrition?.stringOrNull("proteinContent")
            ?.let { firstNutritionIntOrNull(it, MAX_PLAUSIBLE_PROTEIN_GRAMS) },
        ingredients = (obj["recipeIngredient"] ?: obj["ingredients"])?.toIngredients().orEmpty(),
        instructions = obj["recipeInstructions"]?.toInstructionSteps().orEmpty(),
        keywords = obj.keywords(),
        author = obj["author"]?.firstAuthorNameOrNull(),
        sourceUrl = sourceUrl,
    )
}

internal fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonElement.firstImageUrlOrNull(): String? = when (this) {
    is JsonArray -> firstNotNullOfOrNull { it.firstImageUrlOrNull() }
    is JsonObject -> stringOrNull("url")
    is JsonPrimitive -> contentOrNull
}

private fun JsonElement.yieldStrings(): List<String> = when (this) {
    is JsonArray -> flatMap { it.yieldStrings() }
    is JsonPrimitive -> listOfNotNull(contentOrNull)
    is JsonObject -> emptyList()
}

/**
 * Handles the flat `JsonArray` of string primitives schema.org specifies, plus shapes sites publish
 * in practice that it doesn't: a lone string instead of an array, an array of objects (e.g.
 * `HowToSupply`-typed entries with a `name`/`text` field), and ingredients grouped into nested
 * arrays — mirroring how [toInstructionSteps] already handles a typed/nested shape.
 */
private fun JsonElement.toIngredients(): List<ScrapedIngredient> =
    when (this) {
        is JsonArray -> flatMap { it.toIngredients() }
        is JsonObject -> listOfNotNull(cleanHtmlText(stringOrNull("name") ?: stringOrNull("text")))
            .map(::parseIngredient)

        is JsonPrimitive -> listOfNotNull(contentOrNull?.let(::cleanHtmlText)).map(::parseIngredient)
    }

private fun JsonElement.toInstructionSteps(): List<String> = when (this) {
    is JsonArray -> flatMap { it.toInstructionSteps() }

    is JsonObject -> {
        if (typeNames().any { it.equals("HowToSection", ignoreCase = true) }) {
            this["itemListElement"]?.toInstructionSteps().orEmpty()
        } else {
            listOfNotNull(cleanHtmlText(stringOrNull("text") ?: stringOrNull("name")))
        }
    }

    is JsonPrimitive -> contentOrNull?.let(::splitInstructionText).orEmpty()
}

/** A single string blob, split on newlines or — failing that — numbered-list markers ("1. "). */
private fun splitInstructionText(text: String): List<String> {
    val byLine = text.split(Regex("\r?\n+")).map { it.trim() }.filter { it.isNotBlank() }
    val pieces = when {
        byLine.size > 1 -> byLine
        NUMBERED_MARKER.containsMatchIn(text) -> text.split(NUMBERED_MARKER).map { it.trim() }.filter { it.isNotBlank() }
        else -> listOf(text)
    }
    return pieces.mapNotNull(::cleanHtmlText)
}

private val NUMBERED_MARKER = Regex("(?:^|\\s)\\d{1,2}[.)]\\s+")

private fun JsonObject.keywords(): List<String> {
    val all = this["keywords"]?.commaOrArraySeparated().orEmpty() +
        this["recipeCategory"]?.commaOrArraySeparated().orEmpty() +
        this["recipeCuisine"]?.commaOrArraySeparated().orEmpty()
    val seen = HashSet<String>()
    return all.filter { it.isNotBlank() && seen.add(it.lowercase()) }
}

private fun JsonElement.commaOrArraySeparated(): List<String> = when (this) {
    is JsonArray -> flatMap { it.commaOrArraySeparated() }
    is JsonPrimitive -> contentOrNull?.split(",")?.map { it.trim() }.orEmpty()
    is JsonObject -> emptyList()
}

private fun JsonElement.firstAuthorNameOrNull(): String? = when (this) {
    is JsonArray -> firstNotNullOfOrNull { it.firstAuthorNameOrNull() }
    is JsonObject -> stringOrNull("name")
    is JsonPrimitive -> contentOrNull
}
