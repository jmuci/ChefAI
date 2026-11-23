package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.auth.data.network.dto.ApiErrorResponse
import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import com.tenmilelabs.chefai.auth.data.network.dto.GoogleSignInRequest
import com.tenmilelabs.chefai.auth.data.network.dto.LoginRequest
import com.tenmilelabs.chefai.auth.data.network.dto.LogoutRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RefreshTokenRequest
import com.tenmilelabs.chefai.auth.data.network.dto.RegisterRequest
import com.tenmilelabs.chefai.auth.data.network.dto.TokenRefreshResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

// `val`, not `const val`: BuildConfig fields are Java statics, which Kotlin won't accept as
// compile-time constants. Every usage here is a plain string reference, so nothing needs one.
val AUTH_BASE_URL: String = BuildConfig.API_BASE_URL
val REGISTER_ENDPOINT = "$AUTH_BASE_URL/auth/register"
val LOGIN_ENDPOINT = "$AUTH_BASE_URL/auth/login"
val REFRESH_ENDPOINT = "$AUTH_BASE_URL/auth/refresh"
val LOGOUT_ENDPOINT = "$AUTH_BASE_URL/auth/logout"
val GOOGLE_ENDPOINT = "$AUTH_BASE_URL/auth/google"
val GOOGLE_LINK_ENDPOINT = "$AUTH_BASE_URL/auth/google/link"

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

    override suspend fun logout(request: LogoutRequest) {
        val httpResponse = client.post(LOGOUT_ENDPOINT) {
            header("Content-Type", "application/json")
            setBody(request)
            expectSuccess = false
        }

        if (!httpResponse.status.isSuccess()) {
            throw AuthHttpException(
                message = "Logout failed: ${httpResponse.status}",
                statusCode = httpResponse.status.value
            )
        }
    }

    override suspend fun googleSignIn(request: GoogleSignInRequest): AuthResponse {
        val httpResponse = client.post(GOOGLE_ENDPOINT) {
            header("Content-Type", "application/json")
            setBody(request)
            expectSuccess = false
        }

        if (!httpResponse.status.isSuccess()) throw httpResponse.toAuthHttpException("Google sign-in failed")

        return httpResponse.body()
    }

    override suspend fun linkGoogle(request: GoogleSignInRequest) {
        val httpResponse = client.post(GOOGLE_LINK_ENDPOINT) {
            header("Content-Type", "application/json")
            setBody(request)
            expectSuccess = false
        }

        if (!httpResponse.status.isSuccess()) throw httpResponse.toAuthHttpException("Linking Google failed")
    }

    /** Reads the error body leniently: a non-JSON body (e.g. a proxy's 502 page) still yields an exception. */
    private suspend fun HttpResponse.toAuthHttpException(fallbackMessage: String): AuthHttpException {
        val errorBody: ApiErrorResponse? = try {
            body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return AuthHttpException(
            message = errorBody?.message ?: errorBody?.error ?: fallbackMessage,
            statusCode = status.value,
            code = errorBody?.code,
        )
    }
}

/**
 * Exception thrown when an authentication API call fails. [code] is the backend's machine-readable
 * reason (`ErrorResponse.code`), when it sent one.
 */
data class AuthHttpException(
    override val message: String,
    val statusCode: Int,
    val code: String? = null,
) : Exception(message)