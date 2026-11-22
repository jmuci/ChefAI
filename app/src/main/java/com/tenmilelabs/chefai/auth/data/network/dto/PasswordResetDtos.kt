package com.tenmilelabs.chefai.auth.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PasswordResetRequestBody(val email: String)

@Serializable
data class PasswordResetValidateBody(val token: String)

@Serializable
data class PasswordResetConfirmBody(val token: String, val newPassword: String)

/** The backend's `ErrorResponse`, plus the machine-readable `code` the reset routes add. */
@Serializable
data class PasswordResetErrorResponse(
    val message: String? = null,
    val code: String? = null,
)
