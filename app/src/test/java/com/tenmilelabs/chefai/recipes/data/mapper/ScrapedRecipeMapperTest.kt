package com.tenmilelabs.chefai.recipes.data.mapper

import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import com.tenmilelabs.recipescraper.model.LanguageSource
import com.tenmilelabs.recipescraper.model.LanguageConfidence
import com.tenmilelabs.recipescraper.model.DetectedLanguage
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.domain.model.Ingredient
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.core.domain.units.IngredientUnits
import com.tenmilelabs.recipescraper.model.ScrapedIngredient
import com.tenmilelabs.recipescraper.model.ScrapedRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ScrapedRecipeMapperTest {

    private val testUser = User(uuid = UUID.randomUUID(), displayName = "Test", email = "test@test.com", avatarUrl = "")

    private fun scrapedIngredient(name: String, quantity: Double? = 1.0, unit: String? = "cup") =
        ScrapedIngredient(raw = "$quantity $unit $name", quantity = quantity, unit = unit, name = name)

    private fun minimalScrapedRecipe(
        title: String = "Test Recipe",
        description: String? = "A description",
        imageUrl: String? = "https://example.com/img.jpg",
        prepTimeMinutes: Int? = 10,
        cookTimeMinutes: Int? = 20,
        totalTimeMinutes: Int? = null,
        servings: Int? = 4,
        caloriesPerServing: Int? = null,
        proteinGramsPerServing: Int? = null,
        ingredients: List<ScrapedIngredient> = emptyList(),
        instructions: List<String> = emptyList(),
        keywords: List<String> = emptyList(),
        author: String? = "A Cook",
        sourceUrl: String = "https://example.com/recipe",
        language: DetectedLanguage? = null,
    ) = ScrapedRecipe(
        title = title,
        description = description,
        imageUrl = imageUrl,
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = cookTimeMinutes,
        totalTimeMinutes = totalTimeMinutes,
        servings = servings,
        caloriesPerServing = caloriesPerServing,
        proteinGramsPerServing = proteinGramsPerServing,
        ingredients = ingredients,
        instructions = instructions,
        keywords = keywords,
        author = author,
        sourceUrl = sourceUrl,
        language = language,
    )

    @Test
    fun `maps scalar fields straight across`() {
        val recipeId = UUID.randomUUID()
        val draft = minimalScrapedRecipe().toRecipeDraft(recipeId, emptyList(), emptyList())

        assertEquals(recipeId, draft.recipeId)
        assertEquals(true, draft.isNewRecipe)
        assertEquals("Test Recipe", draft.title)
        assertEquals("A description", draft.description)
        assertEquals("https://example.com/img.jpg", draft.imageUrl)
        assertEquals("10", draft.prepTimeMinutes)
        assertEquals("20", draft.cookTimeMinutes)
        assertEquals("4", draft.servings)
        assertEquals("https://example.com/recipe", draft.externalUrl)
        assertEquals(1, draft.version)
        assertEquals(emptyList<Any>(), draft.labels)
    }

    @Test
    fun `a scraped recipe always starts PUBLIC`() {
        // Unlike a recipe written from scratch (RecipeDraft's own default is PRIVATE), a scraped
        // recipe is someone else's already-published page.
        val draft = minimalScrapedRecipe().toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals(RecipePrivacy.PUBLIC, draft.privacy)
    }

    @Test
    fun `a root-relative imageUrl is resolved against the source origin`() {
        val draft = minimalScrapedRecipe(imageUrl = "/img/x.jpg", sourceUrl = "https://example.com/recipes/pie")
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("https://example.com/img/x.jpg", draft.imageUrl)
    }

    @Test
    fun `a protocol-relative imageUrl inherits the source scheme`() {
        val draft = minimalScrapedRecipe(imageUrl = "//cdn.example.com/x.jpg", sourceUrl = "https://example.com/recipe")
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("https://cdn.example.com/x.jpg", draft.imageUrl)
    }

    @Test
    fun `an already-absolute imageUrl is left as the caller published it`() {
        val draft = minimalScrapedRecipe(
            imageUrl = "https://cdn.other.com/y.jpg",
            sourceUrl = "https://example.com/recipe",
        ).toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("https://cdn.other.com/y.jpg", draft.imageUrl)
    }

    @Test
    fun `an imageUrl that cannot be resolved falls back to the raw scraped value`() {
        // An unescaped space makes this an invalid URI reference; resolution must not throw or
        // silently drop the value — the raw string is still better than nothing.
        val draft = minimalScrapedRecipe(imageUrl = "/img/has space.jpg", sourceUrl = "https://example.com/recipe")
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("/img/has space.jpg", draft.imageUrl)
    }

    @Test
    fun `null description and imageUrl become blank, not omitted`() {
        val draft = minimalScrapedRecipe(description = null, imageUrl = null)
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("", draft.description)
        assertEquals("", draft.imageUrl)
    }

    @Test
    fun `absent prep time and servings become the string zero, never blank`() {
        val draft = minimalScrapedRecipe(prepTimeMinutes = null, servings = null)
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("0", draft.prepTimeMinutes)
        assertEquals("0", draft.servings)
    }

    @Test
    fun `scraped calories and protein carry through as strings`() {
        val draft = minimalScrapedRecipe(caloriesPerServing = 350, proteinGramsPerServing = 12)
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("350", draft.caloriesPerServing)
        assertEquals("12", draft.proteinGramsPerServing)
    }

    @Test
    fun `absent calories and protein become blank, not the string zero`() {
        // Unlike prep time and servings, nutrition is optional and never blocks toRecipe() — blank
        // means "not entered", matching manual entry in the editor.
        val draft = minimalScrapedRecipe(caloriesPerServing = null, proteinGramsPerServing = null)
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("", draft.caloriesPerServing)
        assertEquals("", draft.proteinGramsPerServing)
    }

    @Test
    fun `cook time falls back to total minus prep when cook time is absent`() {
        val draft = minimalScrapedRecipe(prepTimeMinutes = 10, cookTimeMinutes = null, totalTimeMinutes = 45)
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("35", draft.cookTimeMinutes)
    }

    @Test
    fun `cook time fallback never goes negative`() {
        val draft = minimalScrapedRecipe(prepTimeMinutes = 50, cookTimeMinutes = null, totalTimeMinutes = 45)
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("0", draft.cookTimeMinutes)
    }

    @Test
    fun `cook time is zero when neither cook nor total time is present`() {
        val draft = minimalScrapedRecipe(cookTimeMinutes = null, totalTimeMinutes = null)
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("0", draft.cookTimeMinutes)
    }

    @Test
    fun `ingredients reuse a known catalog id, matched case-insensitively`() {
        val flourId = UUID.randomUUID()
        val known = listOf(Ingredient(uuid = flourId, displayName = "Flour", allergen = null, sourcePrimary = null))
        val scraped = minimalScrapedRecipe(ingredients = listOf(scrapedIngredient("flour")))

        val draft = scraped.toRecipeDraft(UUID.randomUUID(), known, emptyList())

        assertEquals(flourId, draft.ingredients.single().ingredientId)
    }

    @Test
    fun `an unrecognised ingredient mints a new id rather than reusing an unrelated known one`() {
        val flourId = UUID.randomUUID()
        val known = listOf(Ingredient(uuid = flourId, displayName = "Flour", allergen = null, sourcePrimary = null))
        val draft = minimalScrapedRecipe(ingredients = listOf(scrapedIngredient("Dragon Fruit")))
            .toRecipeDraft(UUID.randomUUID(), known, emptyList())

        assertTrue(draft.ingredients.single().ingredientId != flourId)
        assertEquals("Dragon Fruit", draft.ingredients.single().ingredientDisplayName)
    }

    @Test
    fun `an ingredient with no stated amount is stored as no amount, never an invented 1`() {
        // Given lines the scraper could find no amount in — a 1 here used to be scaled into
        // "0.33" on the shopping list
        val scraped = minimalScrapedRecipe(
            ingredients = listOf(
                ScrapedIngredient(raw = "salt, to taste", quantity = null, unit = null, name = "salt, to taste"),
                ScrapedIngredient(
                    raw = "Perejil fresco (una ramita)",
                    quantity = null,
                    unit = null,
                    name = "Perejil fresco (una ramita)",
                ),
            ),
        )

        // When
        val draft = scraped.toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        // Then
        draft.ingredients.forEach { ingredient ->
            assertEquals(IngredientUnits.NO_AMOUNT, ingredient.quantity, 0.0)
            assertFalse(IngredientUnits.hasAmount(ingredient.quantity))
            assertEquals("unit", ingredient.unit)
        }
    }

    @Test
    fun `an amount with no unit keeps its amount and the placeholder unit`() {
        val scraped = minimalScrapedRecipe(
            ingredients = listOf(ScrapedIngredient(raw = "2 eggs", quantity = 2.0, unit = null, name = "eggs")),
        )

        val draft = scraped.toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals(2.0, draft.ingredients.single().quantity, 0.0)
        assertEquals("unit", draft.ingredients.single().unit)
    }

    @Test
    fun `blank-name ingredients are dropped`() {
        val scraped = minimalScrapedRecipe(
            ingredients = listOf(scrapedIngredient("flour"), ScrapedIngredient(raw = "  ", quantity = null, unit = null, name = "  ")),
        )

        val draft = scraped.toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals(1, draft.ingredients.size)
    }

    @Test
    fun `duplicate ingredient names are deduped, keeping the first`() {
        val scraped = minimalScrapedRecipe(
            ingredients = listOf(
                scrapedIngredient("Flour", quantity = 2.0),
                scrapedIngredient("flour", quantity = 5.0),
            ),
        )

        val draft = scraped.toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals(1, draft.ingredients.size)
        assertEquals(2.0, draft.ingredients.single().quantity, 0.0)
    }

    @Test
    fun `instructions become ordered steps`() {
        val draft = minimalScrapedRecipe(instructions = listOf("Step one", "Step two", "Step three"))
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals(listOf(0, 1, 2), draft.steps.map { it.orderIndex })
        assertEquals(listOf("Step one", "Step two", "Step three"), draft.steps.map { it.instruction })
    }

    @Test
    fun `keywords reuse a known tag id, matched case-insensitively`() {
        val dessertId = UUID.randomUUID()
        val known = listOf(Tag(uuid = dessertId, displayName = "Dessert"))
        val draft = minimalScrapedRecipe(keywords = listOf("dessert"))
            .toRecipeDraft(UUID.randomUUID(), emptyList(), known)

        assertEquals(dessertId, draft.tags.single().uuid)
    }

    @Test
    fun `keywords are capped at 8`() {
        val keywords = (1..12).map { "keyword$it" }
        val draft = minimalScrapedRecipe(keywords = keywords).toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals(8, draft.tags.size)
    }

    @Test
    fun `keywords longer than 30 characters are dropped`() {
        val longKeyword = "a".repeat(31)
        val draft = minimalScrapedRecipe(keywords = listOf(longKeyword, "short"))
            .toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals(listOf("short"), draft.tags.map { it.displayName })
    }

    @Test
    fun `a fully-null-optional recipe still produces a draft that does not throw on toRecipe`() {
        val scraped = ScrapedRecipe(
            title = "Bare Recipe",
            description = null,
            imageUrl = null,
            prepTimeMinutes = null,
            cookTimeMinutes = null,
            totalTimeMinutes = null,
            servings = null,
            ingredients = emptyList(),
            instructions = emptyList(),
            keywords = emptyList(),
            author = null,
            sourceUrl = "https://example.com/bare",
        )

        val draft = scraped.toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("0", draft.prepTimeMinutes)
        assertEquals("0", draft.cookTimeMinutes)
        assertEquals("0", draft.servings)
        assertEquals("", draft.caloriesPerServing)
        assertEquals("", draft.proteinGramsPerServing)

        // Regression guard for the constraint that RecipeDraft.toRecipe() throws on blank numerics —
        // calories/protein must stay blank-safe here too, since RecipeDraft.toRecipe() parses them
        // with toIntOrNull(), not toInt().
        draft.toRecipe(testUser)
    }

    // --- Language ---

    @Test
    fun `a confidently detected language needs no confirmation`() {
        val draft = minimalScrapedRecipe(
            language = DetectedLanguage("es", LanguageConfidence.HIGH, LanguageSource.JSON_LD),
        ).toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("es", draft.languageCode)
        assertFalse(draft.languageNeedsConfirmation)
    }

    @Test
    fun `a low-confidence language is kept as the guess but flagged for confirmation`() {
        val draft = minimalScrapedRecipe(
            language = DetectedLanguage("es", LanguageConfidence.LOW, LanguageSource.TEXT),
        ).toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertEquals("es", draft.languageCode)
        assertTrue(draft.languageNeedsConfirmation)
    }

    @Test
    fun `an undetected language is flagged for confirmation`() {
        val draft = minimalScrapedRecipe(language = null).toRecipeDraft(UUID.randomUUID(), emptyList(), emptyList())

        assertNull(draft.languageCode)
        assertTrue(draft.languageNeedsConfirmation)
    }
}
