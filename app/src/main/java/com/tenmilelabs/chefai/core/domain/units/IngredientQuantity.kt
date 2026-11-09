package com.tenmilelabs.chefai.core.domain.units

/**
 * The one rule for which ingredient quantities the app accepts and stores.
 *
 * `NaN` and `±Infinity` are not amounts: the sync and draft `Json` instances refuse to encode them
 * (no special-float support), so one such row used to fail every later push and crash the import
 * save (#336). Negative amounts mean nothing either, and every reader already treats a non-positive
 * amount as "nothing to show" ([IngredientUnits.hasAmount]).
 *
 * Entry points (the editor, the scraped-recipe mapper) accept only what [isEnterable] allows; the
 * serialization boundaries (draft JSON, sync push) run [sanitized] so a bad value already sitting in
 * Room is healed to [IngredientUnits.NO_AMOUNT] instead of poisoning the queue.
 */
object IngredientQuantity {

    /**
     * The largest amount a user can type or an import can bring in. Far above any real recipe line,
     * and far below where arithmetic on it overflows — a pasted `1e307` used to persist and crash
     * every screen that formatted it (#335). Not applied to data already stored or pulled: those are
     * only [sanitized], and the formatters cope with any finite value.
     */
    const val MAX_ENTRY: Double = 1_000_000.0

    /** Whether [quantity] is an amount the app may store and sync: finite and not negative. */
    fun isValid(quantity: Double): Boolean = quantity.isFinite() && quantity >= 0.0

    /** Whether [quantity] may be entered as a new amount: [isValid] and at most [MAX_ENTRY]. */
    fun isEnterable(quantity: Double): Boolean = isValid(quantity) && quantity <= MAX_ENTRY

    /** [quantity] itself when [isValid], otherwise [IngredientUnits.NO_AMOUNT]. */
    fun sanitized(quantity: Double): Double = if (isValid(quantity)) quantity else IngredientUnits.NO_AMOUNT

    /**
     * Whether [text] is acceptable as the editor's quantity field while it is being typed: ASCII
     * digits with at most one `.`, and no more than [MAX_ENTRY] once it parses. Rules out what
     * `String.toDoubleOrNull` would otherwise let through — `"NaN"`, `"Infinity"`, `"1e307"`, `"1d"`,
     * `"-2"`. Blank and a lone `"."` are allowed as input in progress.
     */
    fun isAcceptableInput(text: String): Boolean {
        if (!PLAIN_DECIMAL_INPUT.matches(text)) return false
        val parsed = text.toDoubleOrNull() ?: return true
        return isEnterable(parsed)
    }

    /** Parses a complete editor quantity, or `null` when [text] isn't a plain, enterable amount. */
    fun parseInput(text: String): Double? = text
        .takeIf { PLAIN_DECIMAL_INPUT.matches(it) }
        ?.toDoubleOrNull()
        ?.takeIf { isEnterable(it) }

    private val PLAIN_DECIMAL_INPUT = Regex("\\d*\\.?\\d*")
}
