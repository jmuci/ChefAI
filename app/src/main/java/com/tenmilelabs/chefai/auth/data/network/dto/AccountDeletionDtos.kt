package com.tenmilelabs.chefai.auth.data.network.dto

import kotlinx.serialization.Serializable

/** Response for `GET /api/v1/me/deletion-preview`. */
@Serializable
data class AccountDeletionPreviewResponse(
    val publicRecipeCount: Int,
    val privateRecipeCount: Int,
    val ownsHousehold: Boolean,
)

/**
 * Request for `POST /api/v1/me/delete`. Exactly one of [password] and [googleIdToken] is set (the
 * server answers 400 otherwise). [publicRecipes] is `"DELETE"` or `"ANONYMIZE"`. Null fields are
 * left out of the JSON entirely — the client `Json` doesn't encode defaults.
 */
@Serializable
data class DeleteAccountRequest(
    val password: String? = null,
    val googleIdToken: String? = null,
    val publicRecipes: String? = null,
) {
    override fun toString(): String =
        "DeleteAccountRequest(credential=<redacted>, publicRecipes=$publicRecipes)"
}

/** The `400` body when the public-recipe choice is missing. */
@Serializable
data class AccountDeletionErrorResponse(
    val code: String? = null,
    val message: String? = null,
    val publicRecipeCount: Int? = null,
)
