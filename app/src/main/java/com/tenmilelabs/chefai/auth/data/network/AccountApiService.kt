package com.tenmilelabs.chefai.auth.data.network

import com.tenmilelabs.chefai.BuildConfig
import com.tenmilelabs.chefai.auth.data.network.dto.AccountDeletionErrorResponse
import com.tenmilelabs.chefai.auth.data.network.dto.DeleteAccountRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

/** The `code` the backend puts on a `400` when the public-recipe choice is missing. */
internal const val PUBLIC_RECIPE_CHOICE_REQUIRED = "PUBLIC_RECIPE_CHOICE_REQUIRED"

/**
 * `AuthInterceptor` attaches `Authorization`; both endpoints require it.
 *
 * Telling the two `401`s on `POST /me/delete` apart matters, because only one of them should ever
 * trigger a token refresh. The backend's JWT challenge answers with a **plain-text** body ("Token
 * is not valid or has expired"), while the route's own password check answers with a JSON
 * `ErrorResponse` ("Invalid password"). So a JSON 401 is [DeleteAccountNetworkResult.WrongPassword]
 * and anything else is [DeleteAccountNetworkResult.Unauthenticated].
 */
@Singleton
class AccountApiService @Inject constructor(
    private val client: HttpClient,
) : AccountNetworkDataSource {

    private companion object {
        val BASE_URL = "${BuildConfig.API_BASE_URL}/api/v1/me"
    }

    override suspend fun getDeletionPreview(): AccountDeletionPreviewNetworkResult {
        val response = client.get("$BASE_URL/deletion-preview") { expectSuccess = false }
        return when (response.status) {
            HttpStatusCode.OK -> AccountDeletionPreviewNetworkResult.Success(response.body())
            HttpStatusCode.Unauthorized -> AccountDeletionPreviewNetworkResult.Unauthenticated
            else -> AccountDeletionPreviewNetworkResult.Failed(response.status.value)
        }
    }

    override suspend fun deleteAccount(password: String, publicRecipes: String?): DeleteAccountNetworkResult {
        val response = client.post("$BASE_URL/delete") {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(password = password, publicRecipes = publicRecipes))
            expectSuccess = false
        }
        return when (response.status) {
            HttpStatusCode.NoContent, HttpStatusCode.OK -> DeleteAccountNetworkResult.Deleted
            HttpStatusCode.Unauthorized -> if (response.isJson()) {
                DeleteAccountNetworkResult.WrongPassword
            } else {
                DeleteAccountNetworkResult.Unauthenticated
            }
            HttpStatusCode.BadRequest -> response.badRequestResult()
            HttpStatusCode.Conflict -> DeleteAccountNetworkResult.Conflict
            HttpStatusCode.TooManyRequests -> DeleteAccountNetworkResult.RateLimited
            else -> DeleteAccountNetworkResult.Failed(response.status.value)
        }
    }

    private suspend fun HttpResponse.badRequestResult(): DeleteAccountNetworkResult {
        // Other 400s ("Invalid request format", a blank password) carry a plain ErrorResponse
        // with no `code`; a body that won't parse at all is treated the same way.
        val error = runCatching { body<AccountDeletionErrorResponse>() }.getOrNull()
        val count = error?.publicRecipeCount
        return if (error?.code == PUBLIC_RECIPE_CHOICE_REQUIRED && count != null) {
            DeleteAccountNetworkResult.PublicRecipeChoiceRequired(count)
        } else {
            DeleteAccountNetworkResult.Failed(status.value)
        }
    }

    private fun HttpResponse.isJson(): Boolean =
        contentType()?.match(ContentType.Application.Json) == true
}
