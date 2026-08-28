package com.tenmilelabs.chefai.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * Renders ingredient amounts as text.
 *
 * Two renderings, because the two callers measure differently:
 * - [decimal] is for aggregated totals, where the number answers "how much do I buy" and a decimal
 *   reads perfectly well (the shopping list).
 * - [cooking] is for a single recipe's quantities, which are measured out with cups and spoons —
 *   "⅔ cup" matches the physical tool in a way "0.67 cup" does not.
 */
object QuantityFormat {

    /** "2", "0.5", "1.25" — at most two decimals, no trailing zeros, no scientific notation. */
    fun decimal(value: Double): String {
        // BigDecimal below rejects these outright, and there is nothing sensible to render anyway.
        if (!value.isFinite()) return value.toString()

        val rounded = round(value * 100.0) / 100.0
        if (rounded == floor(rounded) && abs(rounded) < 1e15) return rounded.toLong().toString()

        // Not `Double.toString().trimEnd('0')`: Java switches to scientific notation at 1e7, and
        // trimming zeros off "1.00000000005E10" eats the exponent's digits and silently returns a
        // number ten times smaller. `toPlainString` never uses an exponent, so there is none to eat.
        return BigDecimal(rounded)
            .setScale(DECIMAL_PLACES, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    /**
     * "2", "½", "1½", "⅔" — the same value as [decimal], but with the fractional part rendered as a
     * cooking fraction when it is close enough to one. Anything that doesn't land near a fraction a
     * measuring cup can express falls back to [decimal].
     *
     * [unit] decides whether fractions apply at all. Metric mass and volume are written in decimals
     * by everyone who uses them — "62.5 g", not "62½ g" — and mixing the two styles down one
     * ingredient column looks broken. Cups, spoons, ounces, pounds and countable things keep their
     * fractions. An unrecognised unit gets fractions too, bounded by [FRACTION_CEILING].
     */
    fun cooking(value: Double, unit: String = ""): String {
        if (value < 0 || value >= FRACTION_CEILING || !value.isFinite()) return decimal(value)
        if (normalizeUnit(unit) in DECIMAL_UNITS) return decimal(value)

        val whole = floor(value).toLong()
        val remainder = value - whole
        val nearest = FRACTIONS.minBy { abs(remainder - it.value) }
        if (abs(remainder - nearest.value) > TOLERANCE) return decimal(value)

        return if (whole == 0L) nearest.glyph else "$whole${nearest.glyph}"
    }

    private const val DECIMAL_PLACES = 2

    /**
     * Tight enough that 0.6 stays "0.6" rather than being rounded up to ⅝ (a 0.025 gap), loose
     * enough that a value already rounded to two decimals — 0.67, 0.33 — still resolves.
     */
    private const val TOLERANCE = 0.021

    /**
     * Backstop for units [DECIMAL_UNITS] doesn't know about: above this a fraction stops helping,
     * so "166.67" beats "166⅔" whatever the unit turns out to be.
     */
    private const val FRACTION_CEILING = 20.0

    private fun normalizeUnit(unit: String): String = unit.trim().lowercase().trimEnd('.')

    /** Metric mass and volume, in the spellings and abbreviations the app's data actually carries. */
    private val DECIMAL_UNITS = setOf(
        "g", "gr", "grs", "gram", "grams", "gramme", "grammes",
        "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms",
        "mg", "milligram", "milligrams",
        "ml", "mls", "millilitre", "millilitres", "milliliter", "milliliters",
        "cl", "dl",
        "l", "lt", "ltr", "litre", "litres", "liter", "liters",
    )

    private data class Fraction(val value: Double, val glyph: String)

    /**
     * The fractions a measuring cup or spoon can express. Order is presentation only — [cooking]
     * picks the nearest entry, because neighbours like ⅓ and ⅜ sit closer together than twice
     * [TOLERANCE] and their windows overlap. Whole numbers are deliberately absent: a remainder
     * near zero falls through to [decimal], which keeps "0.01" from collapsing to "0" and lets
     * "2.0" print as plain "2".
     */
    private val FRACTIONS = listOf(
        Fraction(1.0 / 8, "⅛"),
        Fraction(1.0 / 4, "¼"),
        Fraction(1.0 / 3, "⅓"),
        Fraction(3.0 / 8, "⅜"),
        Fraction(1.0 / 2, "½"),
        Fraction(5.0 / 8, "⅝"),
        Fraction(2.0 / 3, "⅔"),
        Fraction(3.0 / 4, "¾"),
        Fraction(7.0 / 8, "⅞"),
    )
}
