package com.tenmilelabs.chefai.recipes.data.mapper

import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.Ingredient
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import com.tenmilelabs.recipescraper.model.ScrapedIngredient
import com.tenmilelabs.recipescraper.model.ScrapedRecipe
import java.net.URI
import java.util.UUID

private const val MAX_KEYWORDS = 8
private const val MAX_KEYWORD_LENGTH = 30
private const val DEFAULT_QUANTITY = 1.0
private const val DEFAULT_UNIT = "unit"

/**
 * Converts a scraped recipe into a [RecipeDraft] ready to seed the editor.
 *
 * Times and servings are never left blank — [RecipeDraft.toRecipe] throws on a blank numeric
 * string, so every value the page didn't publish becomes `"0"` here rather than `""`; Save simply
 * stays disabled until the user fills in what's missing. Ingredients and tags are matched by name
 * (case-insensitively) against the app's existing catalogs before minting a new id, mirroring
 * [com.tenmilelabs.chefai.recipes.ui.editor.RecipeEditorViewModel.selectIngredient] and
 * `addTagByName` — otherwise every import would duplicate catalog rows.
 */
fun ScrapedRecipe.toRecipeDraft(
    recipeId: UUID,
    knownIngredients: List<Ingredient>,
    knownTags: List<Tag>,
): RecipeDraft = RecipeDraft(
    recipeId = recipeId,
    isNewRecipe = true,
    title = title,
    description = description.orEmpty(),
    imageUrl = resolvedImageUrl().orEmpty(),
    prepTimeMinutes = prepTimeMinutes?.toString() ?: "0",
    cookTimeMinutes = cookTimeMinutesOrFallback(),
    servings = servings?.toString() ?: "0",
    externalUrl = sourceUrl,
    ingredients = ingredients.toRecipeIngredients(knownIngredients),
    steps = instructions.toRecipeSteps(),
    tags = keywords.toTags(knownTags),
    labels = emptyList(),
    version = 1,
)

/**
 * Resolves [ScrapedRecipe.imageUrl] against [ScrapedRecipe.sourceUrl] — the scraper's own KDoc
 * flags that the value "may be relative to sourceUrl", and resolution is documented as the caller's
 * job (`:recipe-scraper` reports what a page published without fabricating or defaulting a value).
 * Falls back to the raw value on any malformed input rather than dropping the image outright.
 */
private fun ScrapedRecipe.resolvedImageUrl(): String? =
    imageUrl?.let { url -> runCatching { URI(sourceUrl).resolve(url).toString() }.getOrDefault(url) }

/** Prefers [ScrapedRecipe.cookTimeMinutes]; falls back to total-minus-prep, then to `"0"`. */
private fun ScrapedRecipe.cookTimeMinutesOrFallback(): String {
    cookTimeMinutes?.let { return it.toString() }
    val total = totalTimeMinutes ?: return "0"
    return (total - (prepTimeMinutes ?: 0)).coerceAtLeast(0).toString()
}

private fun List<ScrapedIngredient>.toRecipeIngredients(
    knownIngredients: List<Ingredient>,
): List<RecipeIngredient> {
    val seenNames = HashSet<String>()
    return mapNotNull { ingredient ->
        val name = ingredient.name.trim()
        if (name.isBlank() || !seenNames.add(name.lowercase())) return@mapNotNull null
        val known = knownIngredients.find { it.displayName.equals(name, ignoreCase = true) }
        RecipeIngredient(
            ingredientId = known?.uuid ?: UuidV7Generator.newId(),
            ingredientDisplayName = name,
            quantity = ingredient.quantity ?: DEFAULT_QUANTITY,
            unit = ingredient.unit ?: DEFAULT_UNIT,
            allergenName = null,
            srcCategory = null,
            srcSubcategory = null,
        )
    }
}

private fun List<String>.toRecipeSteps(): List<RecipeStep> = mapIndexed { index, instruction ->
    RecipeStep(uuid = UuidV7Generator.newId(), orderIndex = index, instruction = instruction)
}

private fun List<String>.toTags(knownTags: List<Tag>): List<Tag> = filter { it.length <= MAX_KEYWORD_LENGTH }
    .take(MAX_KEYWORDS)
    .map { keyword ->
        knownTags.find { it.displayName.equals(keyword, ignoreCase = true) }
            ?: Tag(uuid = UuidV7Generator.newId(), displayName = keyword)
    }
