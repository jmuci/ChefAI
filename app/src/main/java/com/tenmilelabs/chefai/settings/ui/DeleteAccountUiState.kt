package com.tenmilelabs.chefai.settings.ui

import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreview
import com.tenmilelabs.chefai.auth.domain.model.PublicRecipeDisposition

/** The "Delete account" section of the settings screen. */
sealed interface DeleteAccountUiState {

    /** No account to delete (anonymous or still-loading session): the section isn't shown. */
    data object Unavailable : DeleteAccountUiState

    /** The section's button, nothing started. */
    data object Idle : DeleteAccountUiState

    /** Fetching `GET /me/deletion-preview` after the button was tapped. */
    data object LoadingPreview : DeleteAccountUiState

    /** The preview couldn't be fetched. Shown under the button, which stays tappable to retry. */
    data class PreviewFailed(val error: DeleteAccountError) : DeleteAccountUiState

    /**
     * The confirmation dialog: what will be deleted, the public-recipe choice when there are any,
     * and the password re-entry.
     *
     * @param publicRecipes `null` until the user picks — there is no preselected default.
     * @param error the latest failed attempt; cleared as soon as the user edits anything.
     */
    data class Confirming(
        val preview: AccountDeletionPreview,
        val publicRecipes: PublicRecipeDisposition? = null,
        val password: String = "",
        val isPasswordVisible: Boolean = false,
        val isSubmitting: Boolean = false,
        val error: DeleteAccountError? = null,
    ) : DeleteAccountUiState {
        val canSubmit: Boolean get() = password.isNotBlank() && !isSubmitting
    }
}

/** Why the last step of the deletion flow failed. Each has its own message. */
enum class DeleteAccountError {
    /** Submitted without picking what happens to public recipes. */
    PUBLIC_RECIPE_CHOICE_MISSING,

    /** The server says there are public recipes the preview didn't show (published since). */
    PUBLIC_RECIPES_CHANGED,
    WRONG_PASSWORD,
    CONFLICT,
    RATE_LIMITED,
    SESSION_EXPIRED,
    PREVIEW_FAILED,
    GENERIC,
}

/** One-shot outcomes the screen acts on, rather than renders. */
sealed interface SettingsEffect {
    /** The account is gone and the device is back to a fresh anonymous session. */
    data object AccountDeleted : SettingsEffect
}
