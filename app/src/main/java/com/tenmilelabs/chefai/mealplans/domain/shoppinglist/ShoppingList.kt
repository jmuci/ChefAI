package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.domain.units.UnitConversion
import com.tenmilelabs.chefai.core.util.QuantityFormat
import com.tenmilelabs.chefai.recipes.domain.scaling.RecipeScaling
import java.util.UUID

/** One line on the list: an ingredient, how much of it, and whether it's been picked up. */
data class ShoppingListItem(
    /** Normalised name; the stable key for the tick row and for `LazyColumn` item keys. */
    val key: String,
    val displayName: String,
    /** e.g. "500 g", "2 tbsp + 100 ml", or `null` when no usable quantity was recorded. */
    val quantityLabel: String?,
    val section: GrocerySection,
    val isChecked: Boolean,
)

/** One aisle's worth of items, alphabetical. */
data class ShoppingListSection(
    val section: GrocerySection,
    val items: List<ShoppingListItem>,
) {
    val checkedCount: Int get() = items.count { it.isChecked }
}

/** A meal plan's shopping list, grouped by grocery-store section in walk order. */
data class ShoppingList(val sections: List<ShoppingListSection>) {
    val totalCount: Int get() = sections.sumOf { it.items.size }
    val checkedCount: Int get() = sections.sumOf { it.checkedCount }
    val isEmpty: Boolean get() = totalCount == 0

    /** Ticked share in `0f..1f`; `0f` for an empty list. */
    val progress: Float get() = if (totalCount == 0) 0f else checkedCount.toFloat() / totalCount
}

/**
 * Turns a plan's recipe ingredients into a grouped, aggregated shopping list.
 *
 * Pure and Android-free so the aggregation rules can be unit-tested directly; the ViewModel only
 * wraps the result in its UI state. Mirrors how [com.tenmilelabs.chefai.mealplans.ui.detail.MealPlanBoard]
 * relates to `MealPlanDetailViewModel`.
 */
object ShoppingListBuilder {

    /**
     * @param ingredients every ingredient row for every distinct recipe in the plan.
     * @param slotCountByRecipe how many slots each recipe fills in the plan. A recipe cooked twice
     *   in a week needs twice the shopping, and [ingredients] carries its rows only once.
     * @param plannedServings the plan's servings-per-meal; `0` or less disables scaling.
     *   A recipe that published no yield of its own is scaled from
     *   [RecipeScaling.DEFAULT_SERVINGS] rather than left alone, matching the details screen.
     * @param checkedKeys item keys already ticked off, from `shopping_list_checks`.
     * @param measurementSystem the units to shop in. Converting before the amounts are grouped is
     *   what lets a cup of flour from one recipe and 125 g of it from another add up to one line
     *   instead of two joined by "+".
     */
    fun build(
        ingredients: List<PlannedIngredient>,
        slotCountByRecipe: Map<UUID, Int>,
        plannedServings: Int,
        checkedKeys: Set<String>,
        measurementSystem: MeasurementSystem = MeasurementSystem.DEFAULT,
    ): ShoppingList {
        data class ScaledRow(val displayName: String, val unit: String, val amount: Double)

        val scaledRows = ingredients
            .filter { it.displayName.isNotBlank() }
            .map { row ->
                // The recipe's own yield, or the assumed default when it never published one —
                // the same fallback the recipe details screen scales from, so a plan and the
                // recipe it came from never disagree about what "one serving" means.
                val servingsFactor = if (plannedServings > 0) {
                    plannedServings.toDouble() / RecipeScaling.baseServings(row.recipeServings)
                } else {
                    1.0
                }
                val slots = slotCountByRecipe[row.recipeId] ?: 1
                // Scale first, convert second — converting first would put the multiplication on
                // top of a rounded value. Same order the recipe details screen applies them in.
                val converted = UnitConversion.convert(
                    quantity = row.quantity * servingsFactor * slots,
                    unit = row.unit,
                    ingredientName = row.displayName,
                    target = measurementSystem,
                )
                ScaledRow(
                    displayName = row.displayName,
                    unit = converted.unit,
                    amount = converted.quantity,
                )
            }

        val items = scaledRows
            .groupBy { nameKey(it.displayName) }
            .map { (key, rows) ->
                val displayName = rows.groupingBy { it.displayName }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                    .first().key

                val quantityLabel = rows
                    .groupBy { it.unit.trim().lowercase() }
                    .mapValues { (_, unitRows) ->
                        unitRows.first().unit.trim() to unitRows.sumOf { it.amount }
                    }
                    .entries
                    .sortedWith(compareBy({ it.key.isEmpty() }, { it.key }))
                    .mapNotNull { (_, unitAndSum) ->
                        val (originalUnit, sum) = unitAndSum
                        if (sum <= 0.0) return@mapNotNull null
                        if (originalUnit.isBlank()) formatQuantity(sum) else "${formatQuantity(sum)} $originalUnit"
                    }
                    .joinToString(" + ")
                    .ifEmpty { null }

                ShoppingListItem(
                    key = key,
                    displayName = displayName,
                    quantityLabel = quantityLabel,
                    section = GrocerySectionClassifier.classify(displayName),
                    isChecked = key in checkedKeys,
                )
            }

        val sections = items
            .groupBy { it.section }
            .toList()
            .sortedBy { (section, _) -> section.ordinal }
            .map { (section, sectionItems) ->
                ShoppingListSection(
                    section = section,
                    items = sectionItems.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }),
                )
            }

        return ShoppingList(sections)
    }

    /** Lowercased, trimmed, internal whitespace collapsed. The tick key and the grouping key. */
    fun nameKey(displayName: String): String =
        displayName.trim().lowercase().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
}

/**
 * Shopping-list amounts are aggregated totals — "how much do I buy" — so they read as decimals
 * rather than the cooking fractions a single recipe's quantities use. See [QuantityFormat].
 */
internal fun formatQuantity(value: Double): String = QuantityFormat.decimal(value)
