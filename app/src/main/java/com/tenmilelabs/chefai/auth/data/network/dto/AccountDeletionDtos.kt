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
 * Request for `POST /api/v1/me/delete`. [publicRecipes] is `"DELETE"` or `"ANONYMIZE"`, and is
 * left out of the JSON entirely when `null` — the client `Json` doesn't encode defaults.
 */
@Serializable
data class DeleteAccountRequest(
    val password: String,
    val publicRecipes: String? = null,
)

/** The `400` body when the public-recipe choice is missing. */
@Serializable
data class AccountDeletionErrorResponse(
    val code: String? = null,
    val message: String? = null,
    val publicRecipeCount: Int? = null,
)
