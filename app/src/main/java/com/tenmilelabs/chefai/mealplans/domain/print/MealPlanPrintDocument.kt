package com.tenmilelabs.chefai.mealplans.domain.print

import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.PlannedIngredient
import java.util.UUID

/** One recipe filling one slot on one day, shaped for printing. */
data class PrintMealEntry(
    val title: String,
    /** Null when the plan isn't [MealType.DINNER_AND_LUNCH] — a single meal a day needs no label. */
    val slotLabel: String?,
    val topIngredients: List<String>,
)

/** One day's column in the printed table. */
data class PrintDayColumn(
    val dayIndex: Int,
    /** "Day 1", matching [com.tenmilelabs.chefai.mealplans.ui.detail.PlannedMeal.dayLabel]. */
    val label: String,
    val meals: List<PrintMealEntry>,
)

/** A row of at most 7 day columns — one printed page. */
data class PrintTableBlock(val columns: List<PrintDayColumn>)

/** A meal plan shaped for printing: a name, and its days chunked into table blocks. */
data class MealPlanPrintDocument(
    val planName: String,
    val blocks: List<PrintTableBlock>,
)

/**
 * Turns a plan's days into a print-ready table, wrapping past 7 day-columns into a new block.
 *
 * Pure and Android-free so the layout rules can be unit-tested directly; mirrors how
 * [com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListBuilder] relates to the
 * shopping list screen.
 */
object MealPlanPrintDocumentBuilder {

    private const val MAX_COLUMNS_PER_ROW = 7
    private const val TOP_INGREDIENTS_COUNT = 3

    /**
     * @param recipeMap titles for every recipe referenced by [mealPlan]. A slot whose recipe is
     *   missing here (not yet synced to this device) is skipped rather than shown as a placeholder.
     * @param ingredients every ingredient row for every distinct recipe in the plan, e.g. from
     *   [com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository.observeIngredientsForRecipes].
     */
    fun build(
        mealPlan: MealPlan,
        recipeMap: Map<UUID, RecipePreview>,
        ingredients: List<PlannedIngredient>,
    ): MealPlanPrintDocument {
        val showsSlotLabels = mealPlan.preferences.mealType == MealType.DINNER_AND_LUNCH
        val ingredientsByRecipe = ingredients.groupBy { it.recipeId }

        val columns = mealPlan.days
            .sortedBy { it.dayIndex }
            .map { day ->
                // Lunch before dinner, matching how the day is eaten (same order as MealPlanBoard.from).
                val meals = MealSlot.entries.mapNotNull { slot ->
                    val recipeId = day.recipeIdFor(slot) ?: return@mapNotNull null
                    val recipe = recipeMap[recipeId] ?: return@mapNotNull null
                    PrintMealEntry(
                        title = recipe.title,
                        slotLabel = if (showsSlotLabels) slot.label else null,
                        topIngredients = topIngredientNames(ingredientsByRecipe[recipeId].orEmpty()),
                    )
                }
                PrintDayColumn(
                    dayIndex = day.dayIndex,
                    label = "Day ${day.dayIndex + 1}",
                    meals = meals,
                )
            }

        return MealPlanPrintDocument(
            planName = mealPlan.name,
            blocks = columns.chunked(MAX_COLUMNS_PER_ROW).map { PrintTableBlock(it) },
        )
    }

    /** The [TOP_INGREDIENTS_COUNT] largest-quantity ingredients, largest first — the only ranking
     *  signal the data has (there's no authored importance/sort order on a recipe's ingredients). */
    private fun topIngredientNames(ingredients: List<PlannedIngredient>): List<String> =
        ingredients
            .sortedWith(compareByDescending<PlannedIngredient> { it.quantity }.thenBy { it.displayName })
            .take(TOP_INGREDIENTS_COUNT)
            .map { it.displayName }
}
