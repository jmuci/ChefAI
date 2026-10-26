package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import com.tenmilelabs.chefai.core.domain.units.CountingUnits
import com.tenmilelabs.chefai.core.domain.units.IngredientDensity
import com.tenmilelabs.chefai.core.domain.units.IngredientUnits
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.domain.units.UnitConversion
import com.tenmilelabs.chefai.core.domain.units.UnitDimension
import com.tenmilelabs.chefai.core.domain.units.UnitNormalizer
import com.tenmilelabs.chefai.core.domain.units.UnitReading
import com.tenmilelabs.chefai.core.domain.units.UnitSystem
import com.tenmilelabs.chefai.recipes.domain.scaling.RecipeScaling
import java.util.UUID

/** One line on the list: an ingredient, how much of it, and whether it's been picked up. */
data class ShoppingListItem(
    /** Normalised name; the stable key for the tick row and for `LazyColumn` item keys. */
    val key: String,
    val displayName: String,
    /**
     * e.g. "500 g", "2 tbsp + 100 ml", "3 cloves", rounded to what a shop sells (see
     * [ShoppingAmountFormat]); `null` when no usable quantity was recorded ("salt, to taste").
     */
    val quantityLabel: String?,
    val section: GrocerySection,
    val isChecked: Boolean,
    /**
     * True when any part of [quantityLabel] was reached by assuming a density. The list is where
     * a number is most likely to be taken literally — it is what you buy — so an estimate has to
     * announce itself here at least as loudly as it does on the recipe screen.
     */
    val isApproximate: Boolean = false,
    /** Display name of whoever checked this off, when [isChecked] and the checker is known. */
    val checkedByName: String? = null,
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
     * @param checkedByNames item key -> display name of whoever checked it, already resolved by
     *   the caller against the cached household's members — this pure builder has no household
     *   concept of its own, mirroring how `ownerDisplayName` is resolved outside `ShoppingListBuilder`
     *   for shared-plan badges elsewhere.
     */
    fun build(
        ingredients: List<PlannedIngredient>,
        slotCountByRecipe: Map<UUID, Int>,
        plannedServings: Int,
        checkedKeys: Set<String>,
        measurementSystem: MeasurementSystem = MeasurementSystem.DEFAULT,
        checkedByNames: Map<String, String> = emptyMap(),
    ): ShoppingList {
        data class ScaledRow(
            // Without any parenthetical, serving or trailing preparation note: "garlic, peeled
            // and crushed" and "Perejil fresco (una ramita)" are still just the garlic and the
            // parsley. See ShoppingItemNames.
            val displayName: String,
            val unit: String,
            val amount: Double,
            val isApproximate: Boolean,
            // The raw name (plus any note lifted off the unit) — what conversion looked a density
            // up by, where "chopped" is a reason not to guess one. Reused when merging totals.
            val densityName: String,
        )

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
                val displayName = ShoppingItemNames.displayName(row.displayName)
                when (val reading = IngredientUnits.read(row.quantity, row.unit)) {
                    // "To taste", "to serve", a line with no amount: the item still goes on the
                    // list, but nothing is scaled or summed into a measured total beside it.
                    is UnitReading.Unmeasured -> ScaledRow(
                        displayName, unit = "", amount = 0.0, isApproximate = false, densityName = row.displayName,
                    )
                    is UnitReading.Measured -> {
                        // Scale first, convert second — converting first would put the
                        // multiplication on top of a rounded value. Same order the recipe details
                        // screen applies them in. A note lifted off the unit ("cup chopped") rides
                        // along on the name, where it stops a density being guessed.
                        val densityName = if (reading.note.isBlank()) row.displayName else "${row.displayName}, ${reading.note}"
                        val converted = UnitConversion.convert(
                            quantity = row.quantity * servingsFactor * slots,
                            unit = reading.unit,
                            ingredientName = densityName,
                            target = measurementSystem,
                        )
                        ScaledRow(
                            displayName = displayName,
                            unit = converted.unit,
                            amount = converted.quantity,
                            isApproximate = converted.isApproximate,
                            densityName = densityName,
                        )
                    }
                }
            }

        val items = scaledRows
            .groupBy { nameKey(it.displayName) }
            .map { (key, rows) ->
                // The most common spelling; on a tie, the first one met. Synonyms share a key, so
                // "Aubergine" and "Egg Plants" are one line shown under one of the two names.
                val displayName = rows.groupingBy { it.displayName }.eachCount().entries
                    .maxBy { it.value }.key

                val totals = rows
                    // Bucket by the unit a spelling *means*, not the spelling itself: two recipes
                    // can say "300 g" and "200 gram" for the same thing, and "300 g + 200 gram" is
                    // not something anyone can shop from. Counting units (clove/cloves, diente/
                    // dientes) merge on their singular and are re-pluralised for the total below;
                    // anything else unrecognised keeps its own wording.
                    .groupBy { bucketKey(it.unit) }
                    .mapValues { (_, unitRows) ->
                        val spelling = unitRows.first().unit.trim()
                        val display = UnitNormalizer.normalize(spelling)?.canonical
                            ?: CountingUnits.resolve(spelling)?.singular
                            ?: spelling
                        display to unitRows.sumOf { it.amount }
                    }
                    .mergeSameDimension()
                    .mergeByDensity(
                        gramsPerMillilitre = sharedDensity(
                            rows.filter { UnitNormalizer.normalize(it.unit) != null }.map { it.densityName },
                        ),
                        target = measurementSystem,
                    )

                val parts = totals.amounts.entries
                    .sortedWith(compareBy({ it.key.isEmpty() }, { it.key }))
                    .filter { (_, unitAndSum) -> unitAndSum.second > 0.0 }
                val quantityLabel = parts
                    .map { (_, unitAndSum) ->
                        val (unit, sum) = unitAndSum
                        val amount = ShoppingAmountFormat.format(sum, unit)
                        // A bare count beside a measure — cherry tomatoes "½ cup + 6" — reads as a
                        // stray number; say what is being counted.
                        if (unit.isBlank() && parts.size > 1) "$amount $BARE_COUNT_LABEL" else amount
                    }
                    .joinToString(" + ")
                    .ifEmpty { null }

                val isChecked = key in checkedKeys
                ShoppingListItem(
                    key = key,
                    displayName = displayName,
                    quantityLabel = quantityLabel,
                    // One estimated contributor — or a density used to merge — makes the whole
                    // total an estimate.
                    isApproximate = totals.isApproximate || rows.any { it.isApproximate },
                    section = GrocerySectionClassifier.classify(displayName),
                    isChecked = isChecked,
                    checkedByName = if (isChecked) checkedByNames[key] else null,
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

    /**
     * The tick key and the grouping key: lowercased, trimmed, internal whitespace collapsed, and
     * regional synonyms folded onto one spelling ("aubergine" and "egg plants" both key
     * "eggplant"). Expects a name already stripped of notes, as [build] passes it.
     */
    fun nameKey(displayName: String): String = ShoppingItemNames.key(displayName)

    private fun bucketKey(unit: String): String =
        UnitNormalizer.normalize(unit)?.canonical
            ?: CountingUnits.resolve(unit)?.singular
            ?: unit.trim().lowercase()

    /** The one density every measured row agrees on; `null` when any row lacks one or they differ. */
    private fun sharedDensity(densityNames: List<String>): Double? =
        densityNames.map { IngredientDensity.gramsPerMillilitre(it) }.distinct().singleOrNull()

    /** Follows a bare count that shares a line with a measured amount: "½ cup + 6 whole". */
    private const val BARE_COUNT_LABEL = "whole"
}

/** An item's per-unit totals after merging, and whether a merge leaned on an assumed density. */
private data class MergedTotals(
    val amounts: Map<String, Pair<String, Double>>,
    val isApproximate: Boolean,
)

/**
 * In Metric or Imperial, folds a volume total and a weight total of one ingredient into a single
 * amount when [gramsPerMillilitre] is known — ADR-013's curated density table is the only sound way
 * across that line. Metric lands on weight and Imperial on volume, the direction
 * [UnitConversion.convert] already takes for these ingredients, and the result is approximate.
 *
 * As written is left alone: it promises the recipes' own units, and "1 cup + 125 g" is what they
 * say. Counting units, bare counts and unrecognised units never merge with anything.
 */
private fun Map<String, Pair<String, Double>>.mergeByDensity(
    gramsPerMillilitre: Double?,
    target: MeasurementSystem,
): MergedTotals {
    val unmerged = MergedTotals(this, isApproximate = false)
    val system = when (target) {
        MeasurementSystem.METRIC -> UnitSystem.METRIC
        MeasurementSystem.IMPERIAL -> UnitSystem.IMPERIAL
        MeasurementSystem.AS_WRITTEN -> return unmerged
    }
    if (gramsPerMillilitre == null) return unmerged

    // mergeSameDimension has already left at most one bucket per dimension within a system.
    fun bucketOf(dimension: UnitDimension) = entries.singleOrNull { (_, unitAndSum) ->
        UnitNormalizer.normalize(unitAndSum.first)?.let { it.dimension == dimension && it.system == system } == true
    }
    val mass = bucketOf(UnitDimension.MASS) ?: return unmerged
    val volume = bucketOf(UnitDimension.VOLUME) ?: return unmerged

    fun inBase(bucket: Map.Entry<String, Pair<String, Double>>): Double {
        val unit = requireNotNull(UnitNormalizer.normalize(bucket.value.first)) { "matched as a known unit" }
        return bucket.value.second * unit.inBaseUnits
    }
    val grams = inBase(mass)
    val millilitres = inBase(volume)

    val merged = when (system) {
        UnitSystem.METRIC -> UnitConversion.present(grams + millilitres * gramsPerMillilitre, UnitDimension.MASS, system)
        UnitSystem.IMPERIAL -> UnitConversion.present(millilitres + grams / gramsPerMillilitre, UnitDimension.VOLUME, system)
    }
    val rest = filterKeys { it != mass.key && it != volume.key }
    return MergedTotals(rest + (merged.unit to (merged.unit to merged.quantity)), isApproximate = true)
}

/**
 * Collapses unit buckets that measure the same dimension in the same system into one — conversion
 * picks each row's unit by its own magnitude, so "500 g" and "1 kg" (or "1¼ cup" and "2 tbsp") of
 * one ingredient would otherwise be listed as two amounts joined by "+". Summed in base units and
 * re-expressed once. Buckets with nothing to merge with are left exactly as they were.
 */
private fun Map<String, Pair<String, Double>>.mergeSameDimension(): Map<String, Pair<String, Double>> {
    val groups = entries.groupBy { (_, unitAndSum) ->
        UnitNormalizer.normalize(unitAndSum.first)?.let { it.dimension to it.system }
    }
    return buildMap {
        for ((dimensionAndSystem, bucket) in groups) {
            if (dimensionAndSystem == null || bucket.size < 2) {
                bucket.forEach { put(it.key, it.value) }
                continue
            }
            val totalInBase = bucket.sumOf { (_, unitAndSum) ->
                val unit = requireNotNull(UnitNormalizer.normalize(unitAndSum.first)) { "grouped as a known unit" }
                unitAndSum.second * unit.inBaseUnits
            }
            val (dimension, system) = dimensionAndSystem
            val merged = UnitConversion.present(totalInBase, dimension, system)
            put(merged.unit, merged.unit to merged.quantity)
        }
    }
}
