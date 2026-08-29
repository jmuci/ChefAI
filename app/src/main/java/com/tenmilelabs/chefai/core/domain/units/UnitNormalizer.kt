package com.tenmilelabs.chefai.core.domain.units

/**
 * Maps the free-text `recipe_ingredients.unit` column onto a [MeasurementUnit].
 *
 * `unit` is a `String` because it is filled by an HTML scraper and by a plain text field in the
 * editor, so it holds whatever a recipe site or a user wrote. Normalising here — at read time, for
 * display only — buys the whole conversion feature without a schema migration, a sync-payload
 * change and backend agreement, which is what turning that column into an enum would cost.
 *
 * Returns `null` for anything that isn't a mass or volume unit. `null` means "leave it alone", and
 * it is the right answer for counting units (`clove`, `can`, `slice`, `pinch`), for the literal
 * `"unit"` that `ScrapedRecipeMapper` writes when a line carried no unit at all, for a blank, and
 * for the long tail of things nobody anticipated (`punnet`, `knob`, `glug`).
 *
 * The synonym table is a deliberate copy of the one in `:recipe-scraper`'s `IngredientTextParser`
 * rather than a shared dependency: that module is a pure HTML parser whose map is `private`, and
 * widening its public API to share thirty lines of spellings would be the worse trade.
 */
object UnitNormalizer {

    /** The unit [raw] names, or `null` if it isn't one this app converts. */
    fun normalize(raw: String): MeasurementUnit? {
        val bare = raw.trim().trim('.', ',').replace(WHITESPACE, " ")
        if (bare.isEmpty()) return null
        // `T` (tablespoon) and `t` (teaspoon) differ only by case in recipe shorthand, so these
        // have to be matched before anything is lowercased.
        CASE_SENSITIVE[bare]?.let { return it }
        return SYNONYMS[bare.lowercase()]
    }

    private val WHITESPACE = Regex("\\s+")

    private val CASE_SENSITIVE: Map<String, MeasurementUnit> = mapOf(
        "T" to MeasurementUnit.TABLESPOON,
        "t" to MeasurementUnit.TEASPOON,
    )

    private val SYNONYMS: Map<String, MeasurementUnit> = buildMap {
        fun unit(target: MeasurementUnit, vararg spellings: String) {
            put(target.canonical, target)
            spellings.forEach { put(it, target) }
        }

        unit(MeasurementUnit.MILLIGRAM, "milligram", "milligrams", "milligramme", "milligrammes")
        unit(MeasurementUnit.GRAM, "gr", "grs", "gram", "grams", "gramme", "grammes")
        unit(MeasurementUnit.KILOGRAM, "kgs", "kilo", "kilos", "kilogram", "kilograms", "kilogramme", "kilogrammes")

        unit(MeasurementUnit.OUNCE, "ounce", "ounces", "ozs")
        unit(MeasurementUnit.POUND, "lbs", "pound", "pounds")

        unit(MeasurementUnit.MILLILITRE, "mls", "milliliter", "milliliters", "millilitre", "millilitres")
        unit(MeasurementUnit.CENTILITRE, "cls", "centiliter", "centiliters", "centilitre", "centilitres")
        unit(MeasurementUnit.DECILITRE, "dls", "deciliter", "deciliters", "decilitre", "decilitres")
        unit(MeasurementUnit.LITRE, "lt", "ltr", "ltrs", "liter", "liters", "litre", "litres")

        unit(MeasurementUnit.TEASPOON, "tsps", "teaspoon", "teaspoons")
        unit(MeasurementUnit.TABLESPOON, "tbsps", "tablespoon", "tablespoons", "tbs", "tbl", "tblsp")
        // "oz" on its own is mass — the overwhelmingly commoner reading in a recipe — so a fluid
        // ounce has to say so.
        unit(MeasurementUnit.FLUID_OUNCE, "floz", "fl. oz", "fluid oz", "fluid ounce", "fluid ounces")
        unit(MeasurementUnit.CUP, "cups", "c")
        unit(MeasurementUnit.PINT, "pints", "pt", "pts")
        unit(MeasurementUnit.QUART, "quarts", "qt", "qts")
        unit(MeasurementUnit.GALLON, "gallons", "gal")
    }
}
