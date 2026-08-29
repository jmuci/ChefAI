package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.recipes.domain.scaling.RecipeScaling
import org.junit.Test
import java.util.UUID

class ShoppingListBuilderTest {

    private fun ingredient(
        recipeId: UUID,
        recipeServings: Int,
        name: String,
        quantity: Double,
        unit: String = "",
    ) = PlannedIngredient(recipeId, recipeServings, name, quantity, unit)

    private fun ShoppingList.allItems() = sections.flatMap { it.items }

    // --- Measurement system ---

    @Test
    fun `converting before grouping merges what would otherwise be two lines`() {
        // The recipes measure the same ingredient differently, which is the whole reason a mixed
        // library needs this: as written they produce "1 cup + 125 g", which is not a shopping
        // instruction anyone can act on.
        val american = UUID.randomUUID()
        val european = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(american, 0, "all-purpose flour", 1.0, "cup"),
            ingredient(european, 0, "all-purpose flour", 125.0, "g"),
        )
        val slots = mapOf(american to 1, european to 1)

        val asWritten = ShoppingListBuilder.build(ingredients, slots, 0, emptySet())
        val metric = ShoppingListBuilder.build(
            ingredients, slots, 0, emptySet(), MeasurementSystem.METRIC,
        )

        assertThat(asWritten.allItems().single().quantityLabel).isEqualTo("1 cup + 125 g")
        assertThat(metric.allItems().single().quantityLabel).isEqualTo("245 g")
    }

    @Test
    fun `an ingredient counted rather than measured is never converted`() {
        val recipeId = UUID.randomUUID()
        val ingredients = listOf(ingredient(recipeId, 0, "garlic", 3.0, "cloves"))

        val list = ShoppingListBuilder.build(
            ingredients, mapOf(recipeId to 1), 0, emptySet(), MeasurementSystem.METRIC,
        )

        assertThat(list.allItems().single().quantityLabel).isEqualTo("3 cloves")
    }

    // --- Aggregation ---

    @Test
    fun `two recipes needing the same ingredient sum into one item`() {
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipeA, 2, "Onion", 1.0, "cup"),
            ingredient(recipeB, 4, "onion", 2.0, "cup"),
        )

        val list = ShoppingListBuilder.build(
            ingredients = ingredients,
            slotCountByRecipe = mapOf(recipeA to 1, recipeB to 1),
            plannedServings = 0,
            checkedKeys = emptySet(),
        )

        val item = list.allItems().single()
        assertThat(item.quantityLabel).isEqualTo("3 cup")
    }

    @Test
    fun `differently-cased spellings collapse into the most frequent one`() {
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val recipeC = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipeA, 0, "olive oil", 1.0, "tbsp"),
            ingredient(recipeB, 0, "olive oil", 1.0, "tbsp"),
            ingredient(recipeC, 0, "Olive Oil", 1.0, "tbsp"),
        )

        val list = ShoppingListBuilder.build(
            ingredients = ingredients,
            slotCountByRecipe = mapOf(recipeA to 1, recipeB to 1, recipeC to 1),
            plannedServings = 0,
            checkedKeys = emptySet(),
        )

        val item = list.allItems().single()
        assertThat(item.displayName).isEqualTo("olive oil")
        assertThat(item.key).isEqualTo("olive oil")
    }

    @Test
    fun `a recipe filling two slots doubles its quantities`() {
        val recipe = UUID.randomUUID()
        val list = ShoppingListBuilder.build(
            ingredients = listOf(ingredient(recipe, 2, "Salt", 1.0)),
            slotCountByRecipe = mapOf(recipe to 2),
            plannedServings = 0,
            checkedKeys = emptySet(),
        )

        assertThat(list.allItems().single().quantityLabel).isEqualTo("2")
    }

    @Test
    fun `planned servings scale a recipe's own servings`() {
        val scaledRecipe = UUID.randomUUID()
        val yieldlessRecipe = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(scaledRecipe, 2, "Flour", 1.0, "cup"),
            ingredient(yieldlessRecipe, 0, "Sugar", 5.0, "g"),
        )

        val list = ShoppingListBuilder.build(
            ingredients = ingredients,
            slotCountByRecipe = mapOf(scaledRecipe to 1, yieldlessRecipe to 1),
            plannedServings = 4,
            checkedKeys = emptySet(),
        )

        val byName = list.allItems().associateBy { it.displayName }
        assertThat(byName.getValue("Flour").quantityLabel).isEqualTo("2 cup")
        // No published yield, so DEFAULT_SERVINGS is assumed — and the plan wants exactly that many.
        assertThat(byName.getValue("Sugar").quantityLabel).isEqualTo("5 g")
    }

    @Test
    fun `a recipe with no published yield is scaled from the assumed default, not left alone`() {
        val recipe = UUID.randomUUID()

        val list = ShoppingListBuilder.build(
            ingredients = listOf(ingredient(recipe, 0, "Sugar", 5.0, "g")),
            slotCountByRecipe = mapOf(recipe to 1),
            plannedServings = RecipeScaling.DEFAULT_SERVINGS / 2,
            checkedKeys = emptySet(),
        )

        // The details screen presents this recipe as DEFAULT_SERVINGS portions and halves its
        // quantities at half that; the list has to buy the same amount or the two disagree.
        assertThat(list.allItems().single().quantityLabel).isEqualTo("2.5 g")
    }

    @Test
    fun `mixed units render side by side ordered by unit`() {
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipeA, 0, "Broth", 2.0, "tbsp"),
            ingredient(recipeB, 0, "Broth", 100.0, "ml"),
        )

        val list = ShoppingListBuilder.build(
            ingredients = ingredients,
            slotCountByRecipe = mapOf(recipeA to 1, recipeB to 1),
            plannedServings = 0,
            checkedKeys = emptySet(),
        )

        assertThat(list.allItems().single().quantityLabel).isEqualTo("100 ml + 2 tbsp")
    }

    @Test
    fun `an item whose only quantities are zero has no quantity label`() {
        val recipe = UUID.randomUUID()
        val list = ShoppingListBuilder.build(
            ingredients = listOf(ingredient(recipe, 0, "Water", 0.0, "cup")),
            slotCountByRecipe = mapOf(recipe to 1),
            plannedServings = 0,
            checkedKeys = emptySet(),
        )

        assertThat(list.allItems().single().quantityLabel).isNull()
    }

    @Test
    fun `blank display names are dropped`() {
        val recipe = UUID.randomUUID()
        val list = ShoppingListBuilder.build(
            ingredients = listOf(ingredient(recipe, 0, "   ", 1.0, "cup")),
            slotCountByRecipe = mapOf(recipe to 1),
            plannedServings = 0,
            checkedKeys = emptySet(),
        )

        assertThat(list.isEmpty).isTrue()
    }

    // --- Grouping and ordering ---

    @Test
    fun `sections come back in declaration order with empty sections dropped`() {
        val recipe = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipe, 0, "Chicken breast", 1.0),
            ingredient(recipe, 0, "Milk", 1.0),
            ingredient(recipe, 0, "Salt", 1.0),
        )

        val list = ShoppingListBuilder.build(
            ingredients = ingredients,
            slotCountByRecipe = mapOf(recipe to 1),
            plannedServings = 0,
            checkedKeys = emptySet(),
        )

        assertThat(list.sections.map { it.section }).containsExactly(
            GrocerySection.MEAT_AND_SEAFOOD,
            GrocerySection.DAIRY_AND_EGGS,
            GrocerySection.SPICES_AND_BAKING,
        ).inOrder()
    }

    @Test
    fun `items within a section are alphabetical, case-insensitively`() {
        val recipe = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipe, 0, "banana", 1.0),
            ingredient(recipe, 0, "Apple", 1.0),
            ingredient(recipe, 0, "cherry", 1.0),
        )

        val list = ShoppingListBuilder.build(
            ingredients = ingredients,
            slotCountByRecipe = mapOf(recipe to 1),
            plannedServings = 0,
            checkedKeys = emptySet(),
        )

        val produce = list.sections.single { it.section == GrocerySection.PRODUCE }
        assertThat(produce.items.map { it.displayName }).containsExactly("Apple", "banana", "cherry").inOrder()
    }

    @Test
    fun `a ticked item keeps its alphabetical position`() {
        val recipe = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipe, 0, "banana", 1.0),
            ingredient(recipe, 0, "Apple", 1.0),
            ingredient(recipe, 0, "cherry", 1.0),
        )

        val list = ShoppingListBuilder.build(
            ingredients = ingredients,
            slotCountByRecipe = mapOf(recipe to 1),
            plannedServings = 0,
            checkedKeys = setOf("banana"),
        )

        val produce = list.sections.single { it.section == GrocerySection.PRODUCE }
        assertThat(produce.items.map { it.displayName }).containsExactly("Apple", "banana", "cherry").inOrder()
        assertThat(produce.items.single { it.displayName == "banana" }.isChecked).isTrue()
        assertThat(produce.items.filter { it.displayName != "banana" }.all { !it.isChecked }).isTrue()
    }

    // --- formatQuantity ---

    @Test
    fun `formatQuantity drops trailing zeros and never uses scientific notation`() {
        assertThat(formatQuantity(2.0)).isEqualTo("2")
        assertThat(formatQuantity(0.5)).isEqualTo("0.5")
        assertThat(formatQuantity(1.005)).isEqualTo("1")
        assertThat(formatQuantity(1.25)).isEqualTo("1.25")
    }
}
