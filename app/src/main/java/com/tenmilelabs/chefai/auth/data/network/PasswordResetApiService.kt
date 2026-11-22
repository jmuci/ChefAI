package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.auth.data.network.dto.PasswordResetConfirmBody
import com.tenmilelabs.chefai.auth.data.network.dto.PasswordResetErrorResponse
import com.tenmilelabs.chefai.auth.data.network.dto.PasswordResetRequestBody
import com.tenmilelabs.chefai.auth.data.network.dto.PasswordResetValidateBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unauthenticated: `AuthInterceptor` leaves a request alone when there is no session, and these
 * routes ignore an `Authorization` header when there is one (a signed-in user can reset too).
 * The raw token and password live only in the request body — never in a log line or a URL.
 */
@Singleton
class PasswordResetApiService @Inject constructor(
    private val client: HttpClient,
) : PasswordResetNetworkDataSource {

    private companion object {
        val BASE_URL = "${BuildConfig.API_BASE_URL}/auth/password-reset"
    }

    override suspend fun requestReset(email: String): PasswordResetNetworkResult =
        post("request", PasswordResetRequestBody(email))

    override suspend fun validateToken(token: String): PasswordResetNetworkResult =
        post("validate", PasswordResetValidateBody(token))

    override suspend fun confirmReset(token: String, newPassword: String): PasswordResetNetworkResult =
        post("confirm", PasswordResetConfirmBody(token, newPassword))

    private suspend inline fun <reified T : Any> post(path: String, requestBody: T): PasswordResetNetworkResult {
        val response = client.post("$BASE_URL/$path") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
            expectSuccess = false
        }
        return when (response.status) {
            HttpStatusCode.Accepted, HttpStatusCode.NoContent, HttpStatusCode.OK -> PasswordResetNetworkResult.Success
            HttpStatusCode.Gone -> PasswordResetNetworkResult.InvalidToken
            HttpStatusCode.BadRequest -> PasswordResetNetworkResult.Validation(response.errorMessage())
            HttpStatusCode.TooManyRequests -> PasswordResetNetworkResult.RateLimited
            else -> PasswordResetNetworkResult.Failed(response.status.value)
        }
    }

    // A 400 whose body isn't the expected JSON still means "bad input" — an empty message lets the
    // UI fall back to its own copy rather than turning it into a transport error.
    private suspend fun HttpResponse.errorMessage(): String =
        runCatching { body<PasswordResetErrorResponse>().message }.getOrNull().orEmpty()
}
