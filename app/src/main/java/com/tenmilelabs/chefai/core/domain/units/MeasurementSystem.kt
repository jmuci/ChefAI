package com.tenmilelabs.chefai.core.domain.units

/**
 * How the user wants a recipe's amounts read back to them.
 *
 * A reading preference, not a property of the recipe: nothing is ever converted on the way into the
 * database, so switching back to [AS_WRITTEN] always returns the exact numbers the recipe was
 * scraped or typed with. Mirrors the "Original / Metric / Imperial" trio the established recipe
 * managers settled on — keeping the untouched original as a real option rather than as the absence
 * of a feature.
 */
enum class MeasurementSystem {
    /** No conversion. The default, so an existing library never changes under the user. */
    AS_WRITTEN,

    /** Grams, kilograms, millilitres, litres. */
    METRIC,

    /** Ounces, pounds, cups, tablespoons, teaspoons. */
    IMPERIAL,
    ;

    companion object {
        val DEFAULT: MeasurementSystem = AS_WRITTEN

        /** [name] back to an entry, falling back to [DEFAULT] for anything unrecognised. */
        fun fromName(name: String?): MeasurementSystem =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
