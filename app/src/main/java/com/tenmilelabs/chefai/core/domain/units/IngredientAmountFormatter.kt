package com.tenmilelabs.chefai.core.domain.units

import com.tenmilelabs.chefai.core.util.QuantityFormat

/** An amount as the user reads it, and whether it rests on an assumed density. */
data class IngredientAmountLabel(val text: String, val isApproximate: Boolean)

/**
 * Converts an ingredient amount into the user's chosen system and renders it as text.
 *
 * The single place the two steps meet, so the recipe details screen and the shopping list can never
 * disagree about how the same amount reads. Pure and Android-free.
 */
object IngredientAmountFormatter {

    /**
     * @param cookingFractions `true` renders "¾ cup" where the unit warrants it, which is what a
     *   single recipe's quantities want; `false` renders plain decimals, which is what an
     *   aggregated shopping-list total wants. Mirrors the split
     *   [QuantityFormat] already draws between its two renderings.
     */
    fun format(
        quantity: Double,
        unit: String,
        ingredientName: String,
        system: MeasurementSystem,
        cookingFractions: Boolean = true,
    ): IngredientAmountLabel {
        // "To taste" has no number to scale or convert: show the phrase alone, or nothing at all
        // for a line that never stated an amount — never the placeholder quantity behind it.
        val reading = IngredientUnits.read(quantity, unit)
        if (reading is UnitReading.Unmeasured) return IngredientAmountLabel(text = reading.phrase, isApproximate = false)

        val converted = UnitConversion.convert(quantity, unit, ingredientName, system)
        val amount = if (cookingFractions) {
            QuantityFormat.cooking(converted.quantity, converted.unit)
        } else {
            QuantityFormat.decimal(converted.quantity)
        }
        // A unitless ingredient reads "2", not "2 ".
        // "clove" reads "cloves" past one. Exact match only: a unit carrying a trailing note keeps
        // it here, where the recipe is read as written.
        val unitLabel = CountingUnits.resolveExact(converted.unit)?.let { CountingUnits.label(it, converted.quantity) }
            ?: UnitNormalizer.withoutPlaceholder(converted.unit)
        val text = if (unitLabel.isBlank()) amount else "$amount $unitLabel"
        return IngredientAmountLabel(text = text, isApproximate = converted.isApproximate)
    }
}
