package com.tenmilelabs.chefai.auth.domain.model

/**
 * What deleting the signed-in account would affect, from `GET /api/v1/me/deletion-preview`.
 * Counts are of live recipes only.
 */
data class AccountDeletionPreview(
    val publicRecipeCount: Int,
    val privateRecipeCount: Int,
    val ownsHousehold: Boolean,
) {
    /** The server refuses a deletion without a [PublicRecipeDisposition] exactly when this holds. */
    val requiresPublicRecipeChoice: Boolean get() = publicRecipeCount > 0
}

/**
 * What happens to the user's public recipes when their account goes. There is deliberately no
 * default: keeping someone's published content has to be their own decision.
 */
enum class PublicRecipeDisposition {
    /** Emptied and tombstoned for every device that has them. */
    DELETE,

    /** Kept live, credited to the shared "Deleted user" account ([DeletedUser.ID]). */
    ANONYMIZE,
}

/** The shared account that anonymized recipes are reassigned to. Part of the sync contract. */
object DeletedUser {
    const val ID = "10000000-0000-0000-0000-0000000000de"
    const val DISPLAY_NAME = "Deleted user"
}

sealed interface AccountDeletionPreviewResult {
    data class Success(val preview: AccountDeletionPreview) : AccountDeletionPreviewResult

    /** The access token was rejected and could not be refreshed. */
    data object SessionExpired : AccountDeletionPreviewResult

    /** Offline, timeout, 5xx or an unreadable response. */
    data object Failed : AccountDeletionPreviewResult
}

sealed interface DeleteAccountResult {
    /** `204`. The account is gone server-side; every credential this device holds is dead. */
    data object Deleted : DeleteAccountResult

    /** `401` from the password re-check — not an expired session. */
    data object WrongPassword : DeleteAccountResult

    /**
     * The user has public recipes and no [PublicRecipeDisposition] was sent — either the preview
     * was stale (they published one since) or the caller skipped the choice. Carries the server's
     * current count.
     */
    data class PublicRecipeChoiceRequired(val publicRecipeCount: Int) : DeleteAccountResult

    /** `409`: they joined a household mid-deletion. Nothing changed; retrying is safe. */
    data object Conflict : DeleteAccountResult

    /** `429`: the `/auth` rate-limit bucket, since this is a password check. */
    data object RateLimited : DeleteAccountResult

    /** The access token was rejected and could not be refreshed. */
    data object SessionExpired : DeleteAccountResult

    /** Offline, timeout, 5xx, or a `400` other than the public-recipe one. */
    data object Failed : DeleteAccountResult
}
