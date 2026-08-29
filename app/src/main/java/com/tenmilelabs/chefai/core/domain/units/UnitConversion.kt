package com.tenmilelabs.chefai.core.domain.units

import kotlin.math.round

/**
 * An amount ready to be rendered: how much, in what unit, and whether the number was arrived at by
 * assuming a density.
 */
data class ConvertedAmount(
    val quantity: Double,
    val unit: String,
    /**
     * True when a volume was turned into a weight (or the reverse) using [IngredientDensity], so
     * the figure is a typical value rather than an exact one. Everything else here is an exact
     * dimensional conversion. The UI marks these with "≈".
     */
    val isApproximate: Boolean,
)

/**
 * Re-expresses an ingredient amount in the measuring system the user asked for.
 *
 * Pure and Android-free, and applied **at display time only** — the same relationship
 * [com.tenmilelabs.chefai.recipes.domain.scaling.RecipeScaling] has to a recipe. Nothing here is
 * ever written back, so switching to [MeasurementSystem.AS_WRITTEN] restores the recipe's own
 * numbers exactly.
 *
 * Apply this **after** scaling, never before: scaling arithmetic run on a converted-and-rounded
 * value compounds the rounding.
 *
 * Three things are deliberately left untouched in every mode:
 * - **Counting units** — `2 cloves garlic`, `1 can tomatoes`, `a pinch of salt`. [UnitNormalizer]
 *   returns `null` for these, and there is nothing to convert.
 * - **Amounts already in the target system.** A recipe written in grams is not "converted" to
 *   grams; it is left as its author wrote it.
 * - **Step text.** An instruction saying "bake at 350°F" keeps saying that, exactly as
 *   `RecipeScaling` leaves steps alone when quantities change.
 */
object UnitConversion {

    /**
     * [quantity] and [unit] re-expressed for [target].
     *
     * @param ingredientName used only to look up a density, and only when crossing between volume
     *   and weight. Pass the display name; matching tolerates the qualifiers recipes carry
     *   ("all-purpose flour, sifted").
     */
    fun convert(
        quantity: Double,
        unit: String,
        ingredientName: String,
        target: MeasurementSystem,
    ): ConvertedAmount {
        val unchanged = ConvertedAmount(quantity, unit, isApproximate = false)
        if (target == MeasurementSystem.AS_WRITTEN) return unchanged
        if (!quantity.isFinite() || quantity <= 0.0) return unchanged

        val source = UnitNormalizer.normalize(unit) ?: return unchanged
        val targetSystem = when (target) {
            MeasurementSystem.METRIC -> UnitSystem.METRIC
            MeasurementSystem.IMPERIAL -> UnitSystem.IMPERIAL
            MeasurementSystem.AS_WRITTEN -> return unchanged
        }
        if (source.system == targetSystem) return unchanged

        val amountInBase = quantity * source.inBaseUnits
        val density = IngredientDensity.gramsPerMillilitre(ingredientName)

        return when (targetSystem) {
            UnitSystem.METRIC -> when (source.dimension) {
                UnitDimension.MASS -> metricMass(amountInBase, isApproximate = false)
                // A European reading an American recipe wants grams for the things they would weigh
                // and millilitres for the things they would pour. The density table is what draws
                // that line; everything absent from it stays a volume, which is always exact.
                UnitDimension.VOLUME ->
                    if (density != null) metricMass(amountInBase * density, isApproximate = true)
                    else metricVolume(amountInBase)
            }

            UnitSystem.IMPERIAL -> when (source.dimension) {
                UnitDimension.VOLUME -> imperialVolume(amountInBase)
                // The mirror case: an American reading a European recipe wants cups for a weight
                // of flour, and has no cup-shaped way to measure 300 g of anything else.
                UnitDimension.MASS ->
                    if (density != null) imperialVolume(amountInBase / density, isApproximate = true)
                    else imperialMass(amountInBase)
            }
        }
    }

    private fun metricMass(grams: Double, isApproximate: Boolean): ConvertedAmount =
        if (grams >= MeasurementUnit.KILOGRAM.inBaseUnits) {
            ConvertedAmount(roundLarge(grams / MeasurementUnit.KILOGRAM.inBaseUnits), MeasurementUnit.KILOGRAM.canonical, isApproximate)
        } else {
            ConvertedAmount(roundGrams(grams), MeasurementUnit.GRAM.canonical, isApproximate)
        }

    private fun metricVolume(millilitres: Double): ConvertedAmount =
        if (millilitres >= MeasurementUnit.LITRE.inBaseUnits) {
            ConvertedAmount(roundLarge(millilitres / MeasurementUnit.LITRE.inBaseUnits), MeasurementUnit.LITRE.canonical, false)
        } else {
            ConvertedAmount(roundMillilitres(millilitres), MeasurementUnit.MILLILITRE.canonical, false)
        }

    private fun imperialMass(grams: Double): ConvertedAmount {
        val ounces = grams / MeasurementUnit.OUNCE.inBaseUnits
        return if (ounces >= OUNCES_PER_POUND) {
            ConvertedAmount(roundCookingFraction(grams / MeasurementUnit.POUND.inBaseUnits), MeasurementUnit.POUND.canonical, false)
        } else {
            ConvertedAmount(roundCookingFraction(ounces), MeasurementUnit.OUNCE.canonical, false)
        }
    }

    /**
     * Millilitres as the largest spoon or cup that gives at least one of itself — a quarter cup
     * beats four tablespoons, and four tablespoons beat twelve teaspoons.
     */
    private fun imperialVolume(millilitres: Double, isApproximate: Boolean = false): ConvertedAmount {
        val unit = when {
            millilitres >= MeasurementUnit.CUP.inBaseUnits * SMALLEST_USEFUL_CUP_FRACTION -> MeasurementUnit.CUP
            millilitres >= MeasurementUnit.TABLESPOON.inBaseUnits -> MeasurementUnit.TABLESPOON
            else -> MeasurementUnit.TEASPOON
        }
        return ConvertedAmount(roundCookingFraction(millilitres / unit.inBaseUnits), unit.canonical, isApproximate)
    }

    /**
     * Rounds to something a person would write down.
     *
     * A cup is 236.5882365 ml, and printing that helps nobody; 240 ml is both what the recipe means
     * and what every conversion chart says. Metric mass keeps a finer grid than metric volume
     * because 5 g matters in a way 5 ml does not.
     */
    internal fun roundGrams(grams: Double): Double = notToZero(grams) {
        when {
            grams >= 100.0 -> roundToNearest(grams, 5.0)
            grams >= 10.0 -> roundToNearest(grams, 1.0)
            else -> roundToNearest(grams, 0.5)
        }
    }

    internal fun roundMillilitres(millilitres: Double): Double = notToZero(millilitres) {
        when {
            millilitres >= 100.0 -> roundToNearest(millilitres, 10.0)
            millilitres >= 20.0 -> roundToNearest(millilitres, 5.0)
            millilitres >= 10.0 -> roundToNearest(millilitres, 1.0)
            else -> roundToNearest(millilitres, 0.5)
        }
    }

    /** Kilograms and litres, where two decimals is already more precision than the source had. */
    private fun roundLarge(value: Double): Double = roundToNearest(value, 0.01)

    /**
     * Snaps to a fraction a measuring cup can express, so
     * [com.tenmilelabs.chefai.core.util.QuantityFormat.cooking] renders "¾ cup" rather than
     * "0.78 cup". Above the point where it stops drawing fractions, whole units read better.
     */
    internal fun roundCookingFraction(value: Double): Double = notToZero(value) {
        if (value >= FRACTION_CEILING) {
            roundToNearest(value, 1.0)
        } else {
            val whole = kotlin.math.floor(value)
            val remainder = value - whole
            val nearest = MEASURABLE_FRACTIONS.minBy { kotlin.math.abs(remainder - it) }
            whole + nearest
        }
    }

    /**
     * Applies [round] but never lets a real amount vanish.
     *
     * Every grid here has a step, and an amount smaller than half a step would otherwise snap to
     * zero — 1 g of saffron read in Imperial is 0.035 oz, which rounds to a flat "0 oz" and tells
     * the cook they need none of it. Falling back to the unrounded value hands the number to
     * [com.tenmilelabs.chefai.core.util.QuantityFormat], which renders "0.04 oz": ungainly, but
     * true, and a signal that the unit is the wrong size for the amount rather than that the
     * amount is nothing. This is the same trap `QuantityFormat.cooking` avoids by leaving whole
     * numbers out of its own fraction table.
     */
    private inline fun notToZero(value: Double, round: () -> Double): Double {
        val rounded = round()
        return if (rounded == 0.0 && value > 0.0) value else rounded
    }

    private fun roundToNearest(value: Double, step: Double): Double = round(value / step) * step

    private const val OUNCES_PER_POUND = 16.0

    /**
     * Below a quarter cup, spoons are the honest answer — nobody measures ⅛ cup, and
     * `QuantityFormat` would have to render it as a fraction of a fraction.
     */
    private const val SMALLEST_USEFUL_CUP_FRACTION = 0.25

    /** Matches `QuantityFormat.FRACTION_CEILING`: past this it renders decimals, not glyphs. */
    private const val FRACTION_CEILING = 20.0

    /**
     * Exactly the fractions
     * [com.tenmilelabs.chefai.core.util.QuantityFormat.cooking] knows a glyph for, plus the two
     * whole-number ends. Snapping to anything else produces a value it would fall back to decimals
     * for, which is the ugly outcome this rounding exists to avoid.
     */
    private val MEASURABLE_FRACTIONS = listOf(
        0.0, 1.0 / 8, 1.0 / 4, 1.0 / 3, 3.0 / 8, 1.0 / 2, 5.0 / 8, 2.0 / 3, 3.0 / 4, 7.0 / 8, 1.0,
    )
}
