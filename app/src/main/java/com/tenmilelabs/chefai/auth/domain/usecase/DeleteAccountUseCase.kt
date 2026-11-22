package com.tenmilelabs.chefai.auth.domain.usecase

import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreview
import com.tenmilelabs.chefai.auth.domain.model.DeleteAccountResult
import com.tenmilelabs.chefai.auth.domain.model.DeletionProof
import com.tenmilelabs.chefai.auth.domain.model.PublicRecipeDisposition
import com.tenmilelabs.chefai.auth.domain.repository.AccountRepository
import com.tenmilelabs.chefai.core.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Deletes the signed-in account, then tears the device down to a fresh anonymous session with an
 * empty database (see [SessionManager.endDeletedAccountSession]).
 *
 * The public-recipe rule lives here: a choice is sent only when [AccountDeletionPreview] says
 * there are public recipes, and is required then — without one this returns
 * [DeleteAccountResult.PublicRecipeChoiceRequired] without calling the server. The server
 * enforces the same rule, so a stale preview still ends in that result, from the server.
 */
class DeleteAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val sessionManager: SessionManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(
        proof: DeletionProof,
        preview: AccountDeletionPreview,
        publicRecipes: PublicRecipeDisposition?,
    ): DeleteAccountResult {
        val disposition = if (preview.requiresPublicRecipeChoice) {
            publicRecipes ?: return DeleteAccountResult.PublicRecipeChoiceRequired(preview.publicRecipeCount)
        } else {
            null
        }

        // Set before the request: if the process dies after the server deletes the account but
        // before endLocalSession runs, the next cold start finishes the wipe (SessionManager).
        sessionManager.setAccountDeletionPending(true)
        val result = accountRepository.deleteAccount(proof, disposition)
        when (result) {
            DeleteAccountResult.Deleted -> endLocalSession()
            // The server answered and didn't delete: nothing to finish later.
            DeleteAccountResult.WrongPassword,
            DeleteAccountResult.Conflict,
            DeleteAccountResult.RateLimited,
            DeleteAccountResult.SessionExpired,
            is DeleteAccountResult.PublicRecipeChoiceRequired -> sessionManager.setAccountDeletionPending(false)
            // No answer, so the server may have deleted it. Kept: the next cold start forces a
            // refresh and only wipes if the account is really gone.
            DeleteAccountResult.Failed -> Unit
        }
        return result
    }

    /**
     * The account is already gone, so a failed wipe can't turn this into a failure — the user
     * would only be asked to delete an account that no longer exists. It is logged instead; the
     * session still ends up anonymous (see [SessionManager.endDeletedAccountSession]).
     */
    private suspend fun endLocalSession() {
        try {
            withContext(ioDispatcher) { sessionManager.endDeletedAccountSession() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Account deleted, but clearing local data failed")
        }
    }
}
