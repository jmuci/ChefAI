package com.tenmilelabs.chefai.developeroptions.domain.model

/**
 * Which ChefAI backend a debug build talks to. Only debug builds can change it — release builds
 * always talk to [REMOTE] and never read the stored choice.
 */
enum class ApiEnvironment {
    /** The developer's own backend, as the emulator sees it (`10.0.2.2:8080`). */
    LOCAL,

    /** The deployed backend (`chefaiApiBaseUrl`). */
    REMOTE;

    companion object {
        /** What a debug build starts on — unchanged from before this setting existed. */
        val DEFAULT: ApiEnvironment = LOCAL

        /** Stored by [name], so a missing or unknown value falls back to [DEFAULT]. */
        fun fromName(name: String?): ApiEnvironment =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * The two base URLs a debug build can choose between, as baked in by the build.
 *
 * @property local the base URL every ChefAI endpoint is built from (`BuildConfig.API_BASE_URL`).
 * @property remote the deployed host the [ApiEnvironment.REMOTE] choice points requests at.
 */
data class ApiBaseUrls(
    val local: String,
    val remote: String,
) {
    fun urlFor(environment: ApiEnvironment): String = when (environment) {
        ApiEnvironment.LOCAL -> local
        ApiEnvironment.REMOTE -> remote
    }
}
