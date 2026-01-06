package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.auth.data.network.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

const val AUTH_BASE_URL = "http://10.0.2.2:8080" // Use 10.0.2.2 for Android emulator to access localhost
const val REGISTER_ENDPOINT = "$AUTH_BASE_URL/auth/register"
const val LOGIN_ENDPOINT = "$AUTH_BASE_URL/auth/login"
const val REFRESH_ENDPOINT = "$AUTH_BASE_URL/auth/refresh"

/**
 * Implementation of AuthNetworkDataSource using Ktor HTTP client.
 */
@Singleton
class AuthApiService @Inject constructor(
    private val client: HttpClient
) : AuthNetworkDataSource {

    override suspend fun register(request: RegisterRequest): AuthResponse {
        val httpResponse = client.post(REGISTER_ENDPOINT) {
            header("Content-Type", "application/json")
            setBody(request)
            expectSuccess = false // Don't throw on non-2xx responses
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody: ApiErrorResponse = httpResponse.body()
            throw AuthHttpException(
                message = errorBody.message ?: errorBody.error ?: "Registration failed",
                statusCode = httpResponse.status.value
            )
        }

        return httpResponse.body()
    }

    override suspend fun login(request: LoginRequest): AuthResponse {
        val httpResponse = client.post(LOGIN_ENDPOINT) {
            header("Content-Type", "application/json")
            setBody(request)
            expectSuccess = false
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody: ApiErrorResponse = httpResponse.body()
            throw AuthHttpException(
                message = errorBody.message ?: errorBody.error ?: "Login failed",
                statusCode = httpResponse.status.value
            )
        }

        return httpResponse.body()
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): TokenRefreshResponse {
        val httpResponse = client.post(REFRESH_ENDPOINT) {
            header("Content-Type", "application/json")
            setBody(request)
            expectSuccess = false
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody: ApiErrorResponse = httpResponse.body()
            throw AuthHttpException(
                message = errorBody.message ?: errorBody.error ?: "Token refresh failed",
                statusCode = httpResponse.status.value
            )
        }

        return httpResponse.body()
    }
}

/**
 * Exception thrown when an authentication API call fails.
 */
data class AuthHttpException(
    override val message: String,
    val statusCode: Int
) : Exception(message)