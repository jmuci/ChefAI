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
 * @property remoteConfigured `false` when the build had no `chefaiApiBaseUrl` and [remote] is only
 *   the `https://api.invalid` placeholder.
 */
data class ApiBaseUrls(
    val local: String,
    val remote: String,
    val remoteConfigured: Boolean = true,
) {
    /**
     * The environment requests actually go to: a stored [ApiEnvironment.REMOTE] choice falls back
     * to [ApiEnvironment.LOCAL] when the build has no remote to offer, rather than sending every
     * request to the placeholder.
     */
    fun effective(environment: ApiEnvironment): ApiEnvironment =
        if (environment == ApiEnvironment.REMOTE && !remoteConfigured) ApiEnvironment.LOCAL else environment

    fun urlFor(environment: ApiEnvironment): String = when (effective(environment)) {
        ApiEnvironment.LOCAL -> local
        ApiEnvironment.REMOTE -> remote
    }
}
