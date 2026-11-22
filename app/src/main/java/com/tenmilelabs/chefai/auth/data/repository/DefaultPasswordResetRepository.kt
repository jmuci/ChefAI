package com.tenmilelabs.chefai.auth.data.repository

import com.tenmilelabs.chefai.auth.data.network.PasswordResetNetworkDataSource
import com.tenmilelabs.chefai.auth.data.network.PasswordResetNetworkResult
import com.tenmilelabs.chefai.auth.domain.model.PasswordResetResult
import com.tenmilelabs.chefai.auth.domain.repository.PasswordResetRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs only the failure's type or HTTP status — never an argument, so the token and password can't
 * reach Logcat.
 */
@Singleton
class DefaultPasswordResetRepository @Inject constructor(
    private val networkDataSource: PasswordResetNetworkDataSource,
) : PasswordResetRepository {

    override suspend fun requestReset(email: String): PasswordResetResult =
        guarded { networkDataSource.requestReset(email) }

    override suspend fun validateToken(token: String): PasswordResetResult =
        guarded { networkDataSource.validateToken(token) }

    override suspend fun confirmReset(token: String, newPassword: String): PasswordResetResult =
        guarded { networkDataSource.confirmReset(token, newPassword) }

    private suspend fun guarded(call: suspend () -> PasswordResetNetworkResult): PasswordResetResult =
        try {
            call().toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w("Password reset call failed: ${e.javaClass.simpleName}")
            PasswordResetResult.Network
        } catch (e: Exception) {
            Timber.w("Password reset call failed: ${e.javaClass.simpleName}")
            PasswordResetResult.Unknown
        }

    private fun PasswordResetNetworkResult.toDomain(): PasswordResetResult = when (this) {
        PasswordResetNetworkResult.Success -> PasswordResetResult.Success
        PasswordResetNetworkResult.InvalidToken -> PasswordResetResult.InvalidToken
        is PasswordResetNetworkResult.Validation -> PasswordResetResult.Validation(message)
        PasswordResetNetworkResult.RateLimited -> PasswordResetResult.RateLimited
        is PasswordResetNetworkResult.Failed -> {
            Timber.w("Password reset call failed with HTTP $statusCode")
            // 5xx is the backend being down, not a bug on our side — same retryable bucket.
            if (statusCode >= 500) PasswordResetResult.Network else PasswordResetResult.Unknown
        }
    }
}
