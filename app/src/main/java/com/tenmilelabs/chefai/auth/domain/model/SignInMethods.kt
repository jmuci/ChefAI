package com.tenmilelabs.chefai.auth.domain.model

/**
 * How the signed-in account can prove who it is (ADR-019). The defaults describe an account from
 * before Google sign-in existed — a password and no linked providers — which is also what an older
 * backend that does not send these fields implies.
 */
data class SignInMethods(
    /** False for a Google-only account: account deletion then confirms with Google, not a password. */
    val hasPassword: Boolean = true,
    /** Linked identity providers, e.g. `"google"`. */
    val providers: List<String> = emptyList(),
) {
    val hasGoogle: Boolean get() = GOOGLE_PROVIDER in providers

    companion object {
        const val GOOGLE_PROVIDER: String = "google"
    }
}
