package com.tenmilelabs.chefai.recipes.domain.scaling

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import org.junit.Test

class RecipeScalingTest {

    private fun ingredient(name: String, quantity: Double, unit: String = "g") = RecipeIngredient(
        ingredientId = UuidV7Generator.newId(),
        ingredientDisplayName = name,
        quantity = quantity,
        unit = unit,
        allergenName = null,
        srcCategory = null,
        srcSubcategory = null,
    )

    @Test
    fun `baseServings - uses the recipe's own yield when it has one`() {
        assertThat(RecipeScaling.baseServings(1)).isEqualTo(1)
        assertThat(RecipeScaling.baseServings(6)).isEqualTo(6)
        assertThat(RecipeScaling.baseServings(24)).isEqualTo(24)
    }

    @Test
    fun `baseServings - falls back to the default when the recipe published no yield`() {
        // `0` is what ScrapedRecipeMapper writes for a page with no recipeYield, and what
        // NetworkRecipe.toDomain hardcodes — both reach the details screen.
        assertThat(RecipeScaling.baseServings(0)).isEqualTo(RecipeScaling.DEFAULT_SERVINGS)
        assertThat(RecipeScaling.baseServings(-3)).isEqualTo(RecipeScaling.DEFAULT_SERVINGS)
    }

    @Test
    fun `servingsRange - an ordinary recipe spans the standard range`() {
        assertThat(RecipeScaling.servingsRange(4))
            .isEqualTo(RecipeScaling.MIN_SERVINGS..RecipeScaling.MAX_SERVINGS)
    }

    @Test
    fun `servingsRange - a batch recipe raises the ceiling to its own yield`() {
        assertThat(RecipeScaling.servingsRange(24)).isEqualTo(1..24)
    }

    @Test
    fun `servingsRange - always contains the base, so a recipe can be read as written`() {
        listOf(1, 4, 10, 24, 100).forEach { base ->
            assertThat(RecipeScaling.servingsRange(base)).contains(base)
        }
    }

    @Test
    fun `scale - doubling the portions doubles every quantity`() {
        val scaled = RecipeScaling.scale(
            ingredients = listOf(ingredient("Spaghetti", 500.0), ingredient("Eggs", 2.0)),
            baseServings = 4,
            targetServings = 8,
        )

        assertThat(scaled.map { it.quantity }).containsExactly(1000.0, 4.0).inOrder()
    }

    @Test
    fun `scale - halving the portions halves every quantity`() {
        val scaled = RecipeScaling.scale(listOf(ingredient("Flour", 300.0)), baseServings = 4, targetServings = 2)

        assertThat(scaled.single().quantity).isEqualTo(150.0)
    }

    @Test
    fun `scale - leaves everything but the quantity untouched`() {
        val original = ingredient("Pecorino Romano", 250.0, unit = "gr")

        val scaled = RecipeScaling.scale(listOf(original), baseServings = 4, targetServings = 6).single()

        assertThat(scaled).isEqualTo(original.copy(quantity = 375.0))
    }

    @Test
    fun `scale - returns the same list instance when there is nothing to scale`() {
        val ingredients = listOf(ingredient("Salt", 5.0))

        assertThat(RecipeScaling.scale(ingredients, baseServings = 4, targetServings = 4))
            .isSameInstanceAs(ingredients)
    }

    @Test
    fun `scale - a non-positive base or target is a no-op rather than a divide by zero`() {
        val ingredients = listOf(ingredient("Salt", 5.0))

        assertThat(RecipeScaling.scale(ingredients, baseServings = 0, targetServings = 8))
            .isSameInstanceAs(ingredients)
        assertThat(RecipeScaling.scale(ingredients, baseServings = 4, targetServings = 0))
            .isSameInstanceAs(ingredients)
    }

    @Test
    fun `scale - a zero quantity stays zero`() {
        val scaled = RecipeScaling.scale(listOf(ingredient("Salt, to taste", 0.0)), baseServings = 2, targetServings = 10)

        assertThat(scaled.single().quantity).isEqualTo(0.0)
    }

    @Test
    fun `scale - an empty ingredient list survives`() {
        assertThat(RecipeScaling.scale(emptyList(), baseServings = 4, targetServings = 10)).isEmpty()
    }
}
