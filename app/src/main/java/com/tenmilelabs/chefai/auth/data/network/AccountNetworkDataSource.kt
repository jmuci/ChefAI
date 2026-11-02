package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.auth.data.network.dto.AccountDeletionPreviewResponse

/** Network data source for operations on the signed-in account itself. */
interface AccountNetworkDataSource {

    /** `GET /api/v1/me/deletion-preview`. Throws on network failure. */
    suspend fun getDeletionPreview(): AccountDeletionPreviewNetworkResult

    /**
     * `POST /api/v1/me/delete`. [publicRecipes] is the raw `"DELETE"`/`"ANONYMIZE"` value, or
     * `null` to leave it out. Throws on network failure.
     */
    suspend fun deleteAccount(password: String, publicRecipes: String?): DeleteAccountNetworkResult
}

sealed interface AccountDeletionPreviewNetworkResult {
    data class Success(val response: AccountDeletionPreviewResponse) : AccountDeletionPreviewNetworkResult

    /** The JWT was rejected — expired or revoked. Refreshable. */
    data object Unauthenticated : AccountDeletionPreviewNetworkResult
    data class Failed(val statusCode: Int) : AccountDeletionPreviewNetworkResult
}

sealed interface DeleteAccountNetworkResult {
    data object Deleted : DeleteAccountNetworkResult

    /** A `401` from the route's own password check, as opposed to [Unauthenticated]. */
    data object WrongPassword : DeleteAccountNetworkResult
    data class PublicRecipeChoiceRequired(val publicRecipeCount: Int) : DeleteAccountNetworkResult

    /** The JWT was rejected — expired or revoked. Refreshable. */
    data object Unauthenticated : DeleteAccountNetworkResult
    data object Conflict : DeleteAccountNetworkResult
    data object RateLimited : DeleteAccountNetworkResult
    data class Failed(val statusCode: Int) : DeleteAccountNetworkResult
}
