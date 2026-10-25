package com.tenmilelabs.chefai.core.domain.units

/** What a `recipe_ingredients` row's free-text `quantity`/`unit` pair actually says. */
sealed interface UnitReading {

    /**
     * There is no amount to measure or buy — "to taste", "to serve", "a sprinkling", or a line
     * imported with no number at all ([IngredientUnits.NO_AMOUNT]). The row's quantity is a
     * placeholder and must not be scaled, summed or shown.
     *
     * @property phrase the unit column's own wording ("To taste") for display, blank when the row
     *   simply had no amount.
     */
    data class Unmeasured(val phrase: String) : UnitReading

    /**
     * A real amount in [unit]: a measuring unit, a counting unit, a unit nobody anticipated, or
     * blank for a bare count ("2" eggs).
     *
     * @property note a descriptor that was lifted off the unit column ("chopped" from
     *   "tbs chopped", "large" from "large"), blank when there was none. It describes the
     *   ingredient, not the amount.
     */
    data class Measured(val unit: String, val note: String = "") : UnitReading
}

/**
 * Reads the unit column for what it means rather than what it says.
 *
 * `recipe_ingredients.unit` is free text, and the catalog data synced from the backend (imported
 * from TheMealDB) writes whatever followed the number into it: `"sliced"`, `"finely chopped"`,
 * `"large"`, `"tbs chopped"`, and — for a measure with no number at all — the whole phrase behind
 * an invented `1`: `"To taste"`, `"To serve"`, `"Sprinkling"`. Taken literally those read
 * "0.33 finely chopped" and "0.5 To taste + 0.5 tsp" on a scaled shopping list.
 *
 * Pure and read-time only, like [UnitNormalizer]: nothing is written back, so no migration or
 * sync-payload change is needed and existing rows are read correctly too.
 */
object IngredientUnits {

    /**
     * The quantity stored for an ingredient with no stated amount ("salt, to taste").
     *
     * Zero rather than `null` because `quantity` is `NOT NULL` in Room, in the sync payload and in
     * Postgres; zero already scales to zero, and every reader treats a non-positive amount as
     * "nothing to show". Never `1`, which reads as a real amount and gets scaled into "0.33".
     */
    const val NO_AMOUNT: Double = 0.0

    /** Whether [quantity] is a real amount rather than [NO_AMOUNT]. */
    fun hasAmount(quantity: Double): Boolean = quantity.isFinite() && quantity > 0.0

    /** What [quantity] of [unit] means. See [UnitReading]. */
    fun read(quantity: Double, unit: String): UnitReading {
        if (!hasAmount(quantity)) return UnitReading.Unmeasured(phrase = "")

        val raw = UnitNormalizer.withoutPlaceholder(unit).trim()
        if (raw.isEmpty()) return UnitReading.Measured(unit = "")

        // A unit exactly as written ("tablespoons", "fl oz"), or a counting unit that already
        // tolerates a trailing note ("clove peeled crushed") — left alone for the callers that
        // already understand it.
        if (UnitNormalizer.normalize(raw) != null || CountingUnits.resolve(raw) != null) {
            return UnitReading.Measured(unit = raw)
        }

        // A real unit followed by anything at all: "tbs chopped", "cloves minced", "tsp ground".
        // The amount is in the unit; the rest describes the ingredient.
        val tokens = raw.split(WHITESPACE).filter { it.isNotEmpty() }
        val head = tokens.first().trimEnd(',', '.', ';')
        if (tokens.size > 1 && (UnitNormalizer.normalize(head) != null || CountingUnits.resolveExact(head) != null)) {
            return UnitReading.Measured(unit = head, note = tokens.drop(1).joinToString(" "))
        }

        val words = words(raw)
        if (UNMEASURED_PHRASES.any { phrase -> words.containsRun(phrase) }) {
            return UnitReading.Unmeasured(phrase = raw)
        }

        // Only a descriptor: "sliced", "finely chopped", "large", "whole", "small finely diced".
        // The number in front of it is a count of the ingredient itself.
        val afterSize = words.dropWhile { it in SIZE_WORDS }
        if (afterSize.isEmpty() || PreparationNotes.isNote(afterSize)) {
            return UnitReading.Measured(unit = "", note = raw)
        }

        return UnitReading.Measured(unit = raw)
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(NON_WORD).filter { it.isNotEmpty() }

    private fun List<String>.containsRun(run: List<String>): Boolean =
        run.isNotEmpty() && (0..size - run.size).any { start -> subList(start, start + run.size) == run }

    private val WHITESPACE = Regex("\\s+")

    /** Splits on anything but letters (accented ones included) — punctuation, digits, spaces. */
    private val NON_WORD = Regex("[^\\p{L}]+")

    /**
     * Wording that stands in for an amount rather than qualifying one. Matched as a run of whole
     * words anywhere in the unit, so "grated, to taste" and "drizzle (for cooking)" count too.
     * The misspellings are real ones from the synced catalog.
     */
    private val UNMEASURED_PHRASES: List<List<String>> = listOf(
        "to taste", "to serve", "for serving", "to garnish", "for garnish", "garnish",
        "for frying", "for brushing", "for greasing", "to glaze", "as required", "as needed",
        "optional", "sprinkling", "sprinking", "spinkling", "dusting", "drizzle", "topping",
        "part", "parts",
        "al gusto", "para servir", "para decorar", "para freír", "para freir", "opcional",
    ).map { it.split(' ') }

    /** Sizes describe the item counted ("2 large" onions), never the amount. */
    private val SIZE_WORDS: Set<String> = setOf(
        "large", "medium", "small", "whole", "big", "extra",
        "grande", "grandes", "mediano", "mediana", "medianos", "medianas",
        "pequeño", "pequeña", "pequeños", "pequeñas", "entero", "entera", "enteros", "enteras",
    )
}
