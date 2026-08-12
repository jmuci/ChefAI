package com.tenmilelabs.recipescraper.microdata

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.tenmilelabs.recipescraper.ingredient.parseIngredient
import com.tenmilelabs.recipescraper.model.ScrapedRecipe
import com.tenmilelabs.recipescraper.util.cleanHtmlText
import com.tenmilelabs.recipescraper.util.firstIntOrNull
import com.tenmilelabs.recipescraper.util.parseDurationToMinutes

/**
 * Extracts a recipe from schema.org microdata (`itemscope`/`itemprop` attributes) — the second-tier
 * fallback for sites that don't publish JSON-LD. Reuses the same HTML-stripping and yield-integer
 * helpers as the JSON-LD tier ([cleanHtmlText], [firstIntOrNull]) rather than duplicating them.
 *
 * Returns `null` when the document has no `Recipe`-typed microdata scope, or that scope has no
 * usable `name`.
 */
internal fun extractMicrodataRecipe(document: Document, sourceUrl: String): ScrapedRecipe? {
    val scope = document.select("[itemscope][itemtype~=(?i)schema\\.org/Recipe]").firstOrNull() ?: return null
    val title = cleanHtmlText(scope.propertyValue("name")) ?: return null

    return ScrapedRecipe(
        title = title,
        description = cleanHtmlText(scope.propertyValue("description")),
        imageUrl = scope.propertyValue("image"),
        prepTimeMinutes = parseDurationToMinutes(scope.propertyValue("prepTime")),
        cookTimeMinutes = parseDurationToMinutes(scope.propertyValue("cookTime")),
        totalTimeMinutes = parseDurationToMinutes(scope.propertyValue("totalTime")),
        servings = firstIntOrNull(scope.propertyValue("recipeYield")),
        ingredients = scope.propertyValues("recipeIngredient")
            .mapNotNull(::cleanHtmlText)
            .map(::parseIngredient),
        instructions = scope.propertyValues("recipeInstructions").mapNotNull(::cleanHtmlText),
        keywords = scope.keywords(),
        author = cleanHtmlText(scope.propertyValue("author")),
        sourceUrl = sourceUrl,
    )
}

private fun Element.keywords(): List<String> {
    val all = propertyValues("keywords") + propertyValues("recipeCategory") + propertyValues("recipeCuisine")
    val split = all.flatMap { it.split(",") }.map { it.trim() }
    val seen = HashSet<String>()
    return split.filter { it.isNotBlank() && seen.add(it.lowercase()) }
}

/** The first `[itemprop=<name>]` value scoped to this element. */
private fun Element.propertyValue(name: String): String? = propertyValues(name).firstOrNull()

/** Every `[itemprop=<name>]` value scoped to this element, in document order. */
private fun Element.propertyValues(name: String): List<String> =
    select("[itemprop=$name]").mapNotNull { it.microdataValue() }

/**
 * `content` (for `<meta>`) → `datetime` (for `<time>`, where an ISO duration lives) → `src` (for
 * `<img>`) → `href` (for `<a>`/`<link>`) → else the element's own text.
 */
private fun Element.microdataValue(): String? =
    attrOrNull("content") ?: attrOrNull("datetime") ?: attrOrNull("src") ?: attrOrNull("href")
        ?: text().takeIf { it.isNotBlank() }

private fun Element.attrOrNull(key: String): String? = attr(key).takeIf { it.isNotBlank() }
