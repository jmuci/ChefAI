package com.tenmilelabs.chefai.auth.data.repository

import com.tenmilelabs.chefai.auth.data.network.AccountDeletionPreviewNetworkResult
import com.tenmilelabs.chefai.auth.data.network.AccountNetworkDataSource
import com.tenmilelabs.chefai.auth.data.network.DeleteAccountNetworkResult
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreview
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreviewResult
import com.tenmilelabs.chefai.auth.domain.model.DeleteAccountResult
import com.tenmilelabs.chefai.auth.domain.model.PublicRecipeDisposition
import com.tenmilelabs.chefai.auth.domain.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Both calls retry once after a token refresh when the JWT itself is rejected — the same single
 * refresh-then-retry `SyncWorker` and `DefaultRecipeSearchRepository` do, never a loop. A wrong
 * password is a different `401` (see [com.tenmilelabs.chefai.auth.data.network.AccountApiService])
 * and is returned as is: refreshing on it would spend a refresh token for nothing.
 */
@Singleton
class DefaultAccountRepository @Inject constructor(
    private val networkDataSource: AccountNetworkDataSource,
    private val sessionManager: SessionManager,
) : AccountRepository {

    override suspend fun getDeletionPreview(): AccountDeletionPreviewResult = guarded(
        onFailure = AccountDeletionPreviewResult.Failed,
    ) {
        var result = networkDataSource.getDeletionPreview()
        if (result is AccountDeletionPreviewNetworkResult.Unauthenticated) {
            if (sessionManager.refreshToken().isFailure) {
                return@guarded AccountDeletionPreviewResult.SessionExpired
            }
            result = networkDataSource.getDeletionPreview()
        }
        when (result) {
            is AccountDeletionPreviewNetworkResult.Success -> AccountDeletionPreviewResult.Success(
                AccountDeletionPreview(
                    publicRecipeCount = result.response.publicRecipeCount,
                    privateRecipeCount = result.response.privateRecipeCount,
                    ownsHousehold = result.response.ownsHousehold,
                )
            )
            AccountDeletionPreviewNetworkResult.Unauthenticated -> AccountDeletionPreviewResult.SessionExpired
            is AccountDeletionPreviewNetworkResult.Failed -> {
                Timber.w("Deletion preview failed with HTTP ${result.statusCode}")
                AccountDeletionPreviewResult.Failed
            }
        }
    }

    override suspend fun deleteAccount(
        password: String,
        publicRecipes: PublicRecipeDisposition?,
    ): DeleteAccountResult = guarded(onFailure = DeleteAccountResult.Failed) {
        val disposition = publicRecipes?.name
        var result = networkDataSource.deleteAccount(password, disposition)
        if (result is DeleteAccountNetworkResult.Unauthenticated) {
            if (sessionManager.refreshToken().isFailure) {
                return@guarded DeleteAccountResult.SessionExpired
            }
            result = networkDataSource.deleteAccount(password, disposition)
        }
        when (result) {
            DeleteAccountNetworkResult.Deleted -> DeleteAccountResult.Deleted
            DeleteAccountNetworkResult.WrongPassword -> DeleteAccountResult.WrongPassword
            is DeleteAccountNetworkResult.PublicRecipeChoiceRequired ->
                DeleteAccountResult.PublicRecipeChoiceRequired(result.publicRecipeCount)
            DeleteAccountNetworkResult.Unauthenticated -> DeleteAccountResult.SessionExpired
            DeleteAccountNetworkResult.Conflict -> DeleteAccountResult.Conflict
            DeleteAccountNetworkResult.RateLimited -> DeleteAccountResult.RateLimited
            is DeleteAccountNetworkResult.Failed -> {
                Timber.w("Account deletion failed with HTTP ${result.statusCode}")
                DeleteAccountResult.Failed
            }
        }
    }

    /** Network/parse exceptions become [onFailure]; cancellation still propagates. */
    private inline fun <T> guarded(onFailure: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Account request failed")
        onFailure
    }
}
