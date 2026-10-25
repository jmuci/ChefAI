package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import com.tenmilelabs.chefai.core.domain.units.CountingUnits
import com.tenmilelabs.chefai.core.domain.units.MeasurementUnit
import com.tenmilelabs.chefai.core.domain.units.UnitConversion
import com.tenmilelabs.chefai.core.domain.units.UnitNormalizer
import com.tenmilelabs.chefai.core.domain.units.UnitSystem
import com.tenmilelabs.chefai.core.util.QuantityFormat
import kotlin.math.ceil

/**
 * Renders one aggregated shopping-list total — "how much do I buy" — at a precision a shop sells.
 *
 * Scaling a recipe for 6 down to 2 servings leaves thirds everywhere, and printing them raw reads
 * "133.33 g", "0.12 cup", "0.33 clove". Applied last, after scaling, conversion and summing, so it
 * never rounds a value that is then multiplied, and [com.tenmilelabs.chefai.core.domain.units.IngredientAmountFormatter]
 * (the recipe screen) is untouched:
 * - **Metric** mass and volume snap to the same grids conversion already uses (5 g / 10 ml steps
 *   at size), rendered as decimals.
 * - **Imperial** cups, spoons, ounces and pounds snap to a measurable fraction and render as one
 *   ("⅛ cup", "½ tbsp").
 * - **Everything else is counted** — cloves, cans, a bare "2" onions, units nobody anticipated —
 *   and rounds *up* to a whole one: nobody can buy a third of an onion, and buying too little is
 *   the worse mistake.
 */
internal object ShoppingAmountFormat {

    /** [amount] of [unit] as list text, e.g. "135 g", "⅛ cup", "3 cloves", "1". [unit] may be blank. */
    fun format(amount: Double, unit: String): String {
        val measured = UnitNormalizer.normalize(unit)
        val rounded = when {
            measured == null -> roundUpToWhole(amount)
            measured.system == UnitSystem.METRIC -> roundMetric(amount, measured)
            else -> UnitConversion.roundCookingFraction(amount)
        }
        val number = if (measured != null && measured.system == UnitSystem.IMPERIAL) {
            QuantityFormat.cooking(rounded, measured.canonical)
        } else {
            QuantityFormat.decimal(rounded)
        }
        // "clove" reads "cloves" past one — judged on the number shown, not the raw sum.
        val label = CountingUnits.resolveExact(unit)?.let { CountingUnits.label(it, rounded) } ?: unit
        return if (label.isBlank()) number else "$number $label"
    }

    private fun roundMetric(amount: Double, unit: MeasurementUnit): Double = when (unit) {
        MeasurementUnit.GRAM -> UnitConversion.roundGrams(amount)
        MeasurementUnit.MILLILITRE -> UnitConversion.roundMillilitres(amount)
        // kg, l, cl, dl and mg: QuantityFormat's two decimals are already finer than a shop sells.
        else -> amount
    }

    /**
     * Up to the next whole item, never below one. A sum that is a whole number plus floating-point
     * noise (`6 × 2/6 = 2.0000000004`) is that whole number, not the next one.
     */
    private fun roundUpToWhole(amount: Double): Double =
        ceil(amount - WHOLE_TOLERANCE).coerceAtLeast(1.0)

    private const val WHOLE_TOLERANCE = 0.01
}
