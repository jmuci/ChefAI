package com.tenmilelabs.chefai.core.domain.model

/**
 * The languages the app offers when the user picks or confirms a recipe's language.
 *
 * A picker vocabulary, not the storage format: recipes store the bare ISO 639-1 [code] (see
 * [Recipe.languageCode]), so a code outside this list — synced from a newer client, or declared
 * by a page in a language the scraper has no profile for — is kept untouched rather than coerced
 * into one of these. The six match the scraper's text-detection profiles (ADR-016).
 */
enum class RecipeLanguage(val code: String) {
    ENGLISH("en"),
    SPANISH("es"),
    FRENCH("fr"),
    ITALIAN("it"),
    PORTUGUESE("pt"),
    GERMAN("de"),
    ;

    companion object {
        val DEFAULT: RecipeLanguage = ENGLISH

        /** The entry for an ISO 639-1 [code], or `null` when it isn't one the app offers. */
        fun fromCode(code: String?): RecipeLanguage? = entries.firstOrNull { it.code == code }

        /**
         * The best starting point for someone whose device is set to [deviceLanguage] (an ISO
         * 639 code, as `Locale.getLanguage()` returns it): that language if offered, else [DEFAULT].
         */
        fun forDeviceLanguage(deviceLanguage: String): RecipeLanguage = fromCode(deviceLanguage) ?: DEFAULT
    }
}
