package com.tenmilelabs.chefai.core.domain.units

/**
 * The one rule for which ingredient quantities the app accepts and stores.
 *
 * `NaN` and `±Infinity` are not amounts: the sync and draft `Json` instances refuse to encode them
 * (no special-float support), so one such row used to fail every later push and crash the import
 * save (#336). Negative amounts mean nothing either, and every reader already treats a non-positive
 * amount as "nothing to show" ([IngredientUnits.hasAmount]).
 *
 * Entry points (the editor, the scraped-recipe mapper) reject what [isValid] refuses; the
 * serialization boundaries (draft JSON, sync push) run [sanitized] so a bad value already sitting in
 * Room is healed to [IngredientUnits.NO_AMOUNT] instead of poisoning the queue.
 */
object IngredientQuantity {

    /** Whether [quantity] is an amount the app may store: finite and not negative. */
    fun isValid(quantity: Double): Boolean = quantity.isFinite() && quantity >= 0.0

    /** [quantity] itself when [isValid], otherwise [IngredientUnits.NO_AMOUNT]. */
    fun sanitized(quantity: Double): Double = if (isValid(quantity)) quantity else IngredientUnits.NO_AMOUNT

    /**
     * Whether [text] is (a prefix of) a plain decimal amount as the editor's quantity field accepts
     * it: ASCII digits with at most one `.`. Rules out what `String.toDoubleOrNull` would otherwise
     * let through — `"NaN"`, `"Infinity"`, `"1e307"`, `"1d"`, `"-2"`.
     */
    fun isPlainDecimalInput(text: String): Boolean = PLAIN_DECIMAL_INPUT.matches(text)

    /** Parses a complete editor quantity, or `null` when [text] isn't a plain, valid amount. */
    fun parseInput(text: String): Double? = text
        .takeIf { isPlainDecimalInput(it) }
        ?.toDoubleOrNull()
        ?.takeIf { isValid(it) }

    private val PLAIN_DECIMAL_INPUT = Regex("\\d*\\.?\\d*")
}
