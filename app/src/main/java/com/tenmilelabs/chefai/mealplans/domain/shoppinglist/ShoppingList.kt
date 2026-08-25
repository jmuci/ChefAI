package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

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
     * @param checkedKeys item keys already ticked off, from `shopping_list_checks`.
     */
    fun build(
        ingredients: List<PlannedIngredient>,
        slotCountByRecipe: Map<UUID, Int>,
        plannedServings: Int,
        checkedKeys: Set<String>,
    ): ShoppingList {
        data class ScaledRow(val displayName: String, val unit: String, val amount: Double)

        val scaledRows = ingredients
            .filter { it.displayName.isNotBlank() }
            .map { row ->
                val servingsFactor = if (plannedServings > 0 && row.recipeServings > 0) {
                    plannedServings.toDouble() / row.recipeServings
                } else {
                    1.0
                }
                val slots = slotCountByRecipe[row.recipeId] ?: 1
                ScaledRow(
                    displayName = row.displayName,
                    unit = row.unit,
                    amount = row.quantity * servingsFactor * slots,
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

/** "2", "0.5", "1.25" — at most two decimals, no trailing zeros, no scientific notation. */
internal fun formatQuantity(value: Double): String {
    val rounded = round(value * 100.0) / 100.0
    return if (rounded == floor(rounded) && abs(rounded) < 1e15) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}
