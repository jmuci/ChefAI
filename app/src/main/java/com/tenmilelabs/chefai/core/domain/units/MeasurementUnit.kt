package com.tenmilelabs.chefai.core.domain.units

/** Whether a unit measures mass or volume. The two never convert into each other without a density. */
enum class UnitDimension { MASS, VOLUME }

/** Which measuring culture a unit belongs to. */
enum class UnitSystem { METRIC, IMPERIAL }

/**
 * A unit the app knows how to convert, with its size in the dimension's base unit — grams for
 * [UnitDimension.MASS], millilitres for [UnitDimension.VOLUME].
 *
 * Deliberately covers *only* mass and volume. Counting units (`clove`, `can`, `slice`, `pinch`, the
 * literal `"unit"` that `ScrapedRecipeMapper` writes for an amount with no unit) are absent, so
 * [UnitNormalizer] returns `null` for them and [UnitConversion] leaves them exactly as written.
 * Anything unrecognised gets the same treatment, which is the right answer for a free-text column
 * fed by arbitrary recipe sites.
 */
enum class MeasurementUnit(
    /** Short form rendered back to the user, and the spelling [QuantityFormat] recognises. */
    val canonical: String,
    val dimension: UnitDimension,
    val system: UnitSystem,
    /** Size of one of these in grams (mass) or millilitres (volume). */
    val inBaseUnits: Double,
) {
    MILLIGRAM("mg", UnitDimension.MASS, UnitSystem.METRIC, 0.001),
    GRAM("g", UnitDimension.MASS, UnitSystem.METRIC, 1.0),
    KILOGRAM("kg", UnitDimension.MASS, UnitSystem.METRIC, 1_000.0),

    OUNCE("oz", UnitDimension.MASS, UnitSystem.IMPERIAL, 28.349523125),
    POUND("lb", UnitDimension.MASS, UnitSystem.IMPERIAL, 453.59237),

    MILLILITRE("ml", UnitDimension.VOLUME, UnitSystem.METRIC, 1.0),
    CENTILITRE("cl", UnitDimension.VOLUME, UnitSystem.METRIC, 10.0),
    DECILITRE("dl", UnitDimension.VOLUME, UnitSystem.METRIC, 100.0),
    LITRE("l", UnitDimension.VOLUME, UnitSystem.METRIC, 1_000.0),

    TEASPOON("tsp", UnitDimension.VOLUME, UnitSystem.IMPERIAL, 4.92892159375),
    TABLESPOON("tbsp", UnitDimension.VOLUME, UnitSystem.IMPERIAL, 14.78676478125),
    FLUID_OUNCE("fl oz", UnitDimension.VOLUME, UnitSystem.IMPERIAL, 29.5735295625),

    /**
     * The **US customary** cup. A scraped page could equally have meant the US legal cup (240 ml),
     * the metric cup (250 ml) or the imperial cup (284 ml) — a spread of some 20% — and nothing in
     * the markup says which. US customary is the commonest by a wide margin on the sites the
     * importer sees, so it is the assumption; see ADR-013.
     */
    CUP("cup", UnitDimension.VOLUME, UnitSystem.IMPERIAL, 236.5882365),

    PINT("pint", UnitDimension.VOLUME, UnitSystem.IMPERIAL, 473.176473),
    QUART("quart", UnitDimension.VOLUME, UnitSystem.IMPERIAL, 946.352946),
    GALLON("gallon", UnitDimension.VOLUME, UnitSystem.IMPERIAL, 3_785.411784),
}
