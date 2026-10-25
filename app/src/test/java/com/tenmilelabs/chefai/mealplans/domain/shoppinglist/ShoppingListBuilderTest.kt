package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.domain.units.IngredientUnits
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
    fun `the placeholder unit is shown as a bare count and merges with a unitless row`() {
        // Catalog and scraped recipes write the literal "unit" for a line with no unit; it must
        // not reach the screen ("2 unit") nor keep "2 unit + 1" apart from "3".
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipeA, 0, "eggs", 2.0, "unit"),
            ingredient(recipeB, 0, "eggs", 1.0, ""),
        )

        val list = ShoppingListBuilder.build(
            ingredients, mapOf(recipeA to 1, recipeB to 1), 0, emptySet(),
        )

        assertThat(list.allItems().single().quantityLabel).isEqualTo("3")
    }

    @Test
    fun `two spellings of the same unit add up instead of sitting side by side`() {
        // Scraped rows say whatever their page said. "300 g + 200 gram" is not a shopping
        // instruction, and it happens without any conversion being involved at all.
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipeA, 0, "flour", 300.0, "g"),
            ingredient(recipeB, 0, "flour", 200.0, "gram"),
        )

        val list = ShoppingListBuilder.build(
            ingredients, mapOf(recipeA to 1, recipeB to 1), 0, emptySet(),
        )

        assertThat(list.allItems().single().quantityLabel).isEqualTo("500 g")
    }

    @Test
    fun `different units of the same dimension and system merge into one amount`() {
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipeA, 0, "potatoes", 500.0, "g"),
            ingredient(recipeB, 0, "potatoes", 1.0, "kg"),
        )

        val list = ShoppingListBuilder.build(
            ingredients, mapOf(recipeA to 1, recipeB to 1), 0, emptySet(), MeasurementSystem.METRIC,
        )

        assertThat(list.allItems().single().quantityLabel).isEqualTo("1.5 kg")
    }

    @Test
    fun `a unit with no canonical form keeps its own wording`() {
        val recipeId = UUID.randomUUID()
        val ingredients = listOf(ingredient(recipeId, 0, "garlic", 3.0, "cloves"))

        val list = ShoppingListBuilder.build(ingredients, mapOf(recipeId to 1), 0, emptySet())

        assertThat(list.allItems().single().quantityLabel).isEqualTo("3 cloves")
    }

    @Test
    fun `garlic written three ways adds up to one line`() {
        // Issue #280: "1 clove + 0.5 clove peeled crushed + 3 cloves". The singular, the plural and
        // the unit that carries a preparation note are all the same garlic.
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val recipeC = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipeA, 0, "Garlic", 1.0, "clove"),
            ingredient(recipeB, 0, "garlic", 0.5, "clove peeled crushed"),
            ingredient(recipeC, 0, "garlic", 3.0, "cloves"),
        )

        val list = ShoppingListBuilder.build(
            ingredients, mapOf(recipeA to 1, recipeB to 1, recipeC to 1), 0, emptySet(),
        )

        // 4.5 cloves, rounded up: half a clove can't be bought on its own.
        assertThat(list.allItems().single().quantityLabel).isEqualTo("5 cloves")
    }

    @Test
    fun `a counting unit is singular when the total is one or less`() {
        val recipeId = UUID.randomUUID()
        val ingredients = listOf(ingredient(recipeId, 0, "garlic", 1.0, "cloves"))

        val list = ShoppingListBuilder.build(ingredients, mapOf(recipeId to 1), 0, emptySet())

        assertThat(list.allItems().single().quantityLabel).isEqualTo("1 clove")
    }

    @Test
    fun `spanish counting units merge and stay spanish`() {
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipeA, 0, "ajo", 1.0, "diente"),
            ingredient(recipeB, 0, "ajo", 2.0, "dientes"),
        )

        val list = ShoppingListBuilder.build(
            ingredients, mapOf(recipeA to 1, recipeB to 1), 0, emptySet(),
        )

        assertThat(list.allItems().single().quantityLabel).isEqualTo("3 dientes")
    }

    @Test
    fun `a trailing preparation note on the name does not split the item`() {
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val ingredients = listOf(
            ingredient(recipeA, 0, "garlic", 2.0, "cloves"),
            ingredient(recipeB, 0, "garlic, peeled and crushed", 1.0, "clove"),
        )

        val list = ShoppingListBuilder.build(
            ingredients, mapOf(recipeA to 1, recipeB to 1), 0, emptySet(),
        )

        val item = list.allItems().single()
        assertThat(item.displayName).isEqualTo("garlic")
        assertThat(item.key).isEqualTo("garlic")
        assertThat(item.quantityLabel).isEqualTo("3 cloves")
    }

    @Test
    fun `a total resting on an assumed density is flagged as approximate`() {
        val recipeId = UUID.randomUUID()
        val flour = listOf(ingredient(recipeId, 0, "all-purpose flour", 1.0, "cup"))
        val stock = listOf(ingredient(recipeId, 0, "chicken stock", 1.0, "cup"))
        val slots = mapOf(recipeId to 1)

        val flourList = ShoppingListBuilder.build(flour, slots, 0, emptySet(), MeasurementSystem.METRIC)
        val stockList = ShoppingListBuilder.build(stock, slots, 0, emptySet(), MeasurementSystem.METRIC)

        // A weight guessed from a density is an estimate; a volume-to-volume conversion is exact.
        assertThat(flourList.allItems().single().isApproximate).isTrue()
        assertThat(stockList.allItems().single().isApproximate).isFalse()
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

    // --- checkedByName ---

    @Test
    fun `a ticked item resolves checkedByName from the checker map`() {
        val recipe = UUID.randomUUID()
        val ingredients = listOf(ingredient(recipe, 0, "onion", 1.0))

        val list = ShoppingListBuilder.build(
            ingredients = ingredients,
            slotCountByRecipe = mapOf(recipe to 1),
            plannedServings = 0,
            checkedKeys = setOf("onion"),
            checkedByNames = mapOf("onion" to "Alex"),
        )

        assertThat(list.allItems().single().checkedByName).isEqualTo("Alex")
    }

    @Test
    fun `an unticked item never shows a checker even if the map has a stale entry for its key`() {
        val recipe = UUID.randomUUID()
        val ingredients = listOf(ingredient(recipe, 0, "onion", 1.0))

        val list = ShoppingListBuilder.build(
            ingredients = ingredients,
            slotCountByRecipe = mapOf(recipe to 1),
            plannedServings = 0,
            checkedKeys = emptySet(),
            checkedByNames = mapOf("onion" to "Alex"),
        )

        assertThat(list.allItems().single().checkedByName).isNull()
    }

    @Test
    fun `a ticked item with no known checker has a null checkedByName`() {
        val recipe = UUID.randomUUID()
        val ingredients = listOf(ingredient(recipe, 0, "onion", 1.0))

        val list = ShoppingListBuilder.build(
            ingredients = ingredients,
            slotCountByRecipe = mapOf(recipe to 1),
            plannedServings = 0,
            checkedKeys = setOf("onion"),
        )

        assertThat(list.allItems().single().checkedByName).isNull()
    }

    // --- Amounts that aren't amounts (no stated quantity, "to taste", descriptors) ---

    /** The production shape: recipes serving 6, planned for 2, each filling one slot. */
    private fun buildForTwo(vararg rows: Pair<UUID, Triple<String, Double, String>>): ShoppingList =
        ShoppingListBuilder.build(
            ingredients = rows.map { (recipe, row) -> ingredient(recipe, 6, row.first, row.second, row.third) },
            slotCountByRecipe = rows.associate { (recipe, _) -> recipe to 1 },
            plannedServings = 2,
            checkedKeys = emptySet(),
        )

    @Test
    fun `an ingredient imported with no amount is listed without one and never scaled`() {
        // Given a line the importer found no amount in, from a recipe scaled 6 -> 2
        val recipe = UUID.randomUUID()

        // When
        val list = buildForTwo(
            recipe to Triple("Sal y pimienta negra recién molida (al gusto)", IngredientUnits.NO_AMOUNT, "unit"),
        )

        // Then it is on the list, with no "0.33" invented for it
        val item = list.allItems().single()
        assertThat(item.displayName).isEqualTo("Sal y pimienta negra recién molida (al gusto)")
        assertThat(item.quantityLabel).isNull()
    }

    @Test
    fun `a to-taste amount is not summed with a measured one`() {
        // Given "0.5 To taste + 0.5 tsp": one recipe salts to taste, another measures it
        val toTaste = UUID.randomUUID()
        val measured = UUID.randomUUID()
        val list = ShoppingListBuilder.build(
            ingredients = listOf(
                ingredient(toTaste, 4, "salt", 1.0, "To taste"),
                ingredient(measured, 4, "salt", 1.0, "tsp"),
            ),
            slotCountByRecipe = mapOf(toTaste to 1, measured to 1),
            plannedServings = 2,
            checkedKeys = emptySet(),
        )

        assertThat(list.allItems().single().quantityLabel).isEqualTo("½ tsp")
    }

    @Test
    fun `phrases standing in for an amount give no quantity at all`() {
        val recipe = UUID.randomUUID()
        val list = buildForTwo(
            recipe to Triple("parsley", 1.0, "to serve"),
            recipe to Triple("cheese", 1.0, "Sprinkling"),
            recipe to Triple("gin", 1.0, "part"),
        )

        assertThat(list.allItems().map { it.quantityLabel }).containsExactly(null, null, null)
    }

    @Test
    fun `a preparation note or size in the unit column is a count of the ingredient`() {
        // Given "1 sliced", "1 finely chopped", "1 large" and "1 whole" — descriptors stored as units
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val recipeC = UUID.randomUUID()

        // When
        val list = ShoppingListBuilder.build(
            ingredients = listOf(
                ingredient(recipeA, 0, "onion", 1.0, "sliced"),
                ingredient(recipeB, 0, "onion", 1.0, "finely chopped"),
                ingredient(recipeC, 0, "onion", 1.0, "large"),
                ingredient(recipeC, 0, "lemon", 1.0, "whole"),
            ),
            slotCountByRecipe = mapOf(recipeA to 1, recipeB to 1, recipeC to 1),
            plannedServings = 0,
            checkedKeys = emptySet(),
        )

        // Then
        val byName = list.allItems().associateBy { it.displayName }
        assertThat(byName.getValue("onion").quantityLabel).isEqualTo("3")
        assertThat(byName.getValue("lemon").quantityLabel).isEqualTo("1")
    }

    @Test
    fun `a real unit carrying a note merges with the same unit written plainly`() {
        val recipeA = UUID.randomUUID()
        val recipeB = UUID.randomUUID()
        val recipeC = UUID.randomUUID()
        val list = ShoppingListBuilder.build(
            ingredients = listOf(
                ingredient(recipeA, 0, "parsley", 1.0, "tbs chopped"),
                ingredient(recipeB, 0, "parsley", 1.0, "tablespoon"),
                ingredient(recipeC, 0, "parsley", 1.0, "tbls"),
            ),
            slotCountByRecipe = mapOf(recipeA to 1, recipeB to 1, recipeC to 1),
            plannedServings = 0,
            checkedKeys = emptySet(),
        )

        assertThat(list.allItems().single().quantityLabel).isEqualTo("3 tbsp")
    }

    // --- Rounding to what a shop sells ---

    @Test
    fun `scaled totals are rounded for shopping rather than shown as raw decimals`() {
        // Given recipes for 6 scaled to 2: every amount becomes a third
        val recipe = UUID.randomUUID()

        // When
        val list = buildForTwo(
            recipe to Triple("rice", 400.0, "g"),
            recipe to Triple("garlic", 1.0, "clove"),
            recipe to Triple("onion", 1.0, ""),
            recipe to Triple("milk", 0.375, "cup"),
            recipe to Triple("butter", 1.5, "tbsp"),
        )

        // Then
        val byName = list.allItems().associate { it.displayName to it.quantityLabel }
        assertThat(byName).containsExactly(
            "rice", "135 g",
            "garlic", "1 clove",
            "onion", "1",
            "milk", "⅛ cup",
            "butter", "½ tbsp",
        )
    }

    @Test
    fun `rounding happens after conversion so metric and imperial both read cleanly`() {
        val recipe = UUID.randomUUID()
        val slots = mapOf(recipe to 1)

        val metric = ShoppingListBuilder.build(
            listOf(ingredient(recipe, 6, "chicken stock", 1.0, "cup")), slots, 2, emptySet(), MeasurementSystem.METRIC,
        )
        val imperial = ShoppingListBuilder.build(
            listOf(ingredient(recipe, 6, "chicken stock", 250.0, "ml")), slots, 2, emptySet(), MeasurementSystem.IMPERIAL,
        )

        // ⅓ cup is 78.9 ml; 250 ml ÷ 3 is 83.3 ml, which is about ⅓ cup
        assertThat(metric.allItems().single().quantityLabel).isEqualTo("80 ml")
        assertThat(imperial.allItems().single().quantityLabel).isEqualTo("⅓ cup")
    }
}
