package com.tenmilelabs.chefai.auth.data.network

/** Network data source for `POST /auth/password-reset/{request,validate,confirm}`. */
interface PasswordResetNetworkDataSource {
    /** Throws on a transport failure; every HTTP status is a [PasswordResetNetworkResult]. */
    suspend fun requestReset(email: String): PasswordResetNetworkResult

    suspend fun validateToken(token: String): PasswordResetNetworkResult

    suspend fun confirmReset(token: String, newPassword: String): PasswordResetNetworkResult
}

sealed interface PasswordResetNetworkResult {
    /** `202` / `204`. */
    data object Success : PasswordResetNetworkResult

    /** `410`. */
    data object InvalidToken : PasswordResetNetworkResult

    /** `400`, with the server's message (blank when the body had none). */
    data class Validation(val message: String) : PasswordResetNetworkResult

    data object RateLimited : PasswordResetNetworkResult
    data class Failed(val statusCode: Int) : PasswordResetNetworkResult
}
