package com.tenmilelabs.chefai.auth.domain.repository

import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreviewResult
import com.tenmilelabs.chefai.auth.domain.model.DeleteAccountResult
import com.tenmilelabs.chefai.auth.domain.model.PublicRecipeDisposition

/**
 * Server-side operations on the signed-in account itself. Direct REST, no dirty queue: an
 * account deletion can't be queued offline and replayed later, it needs the password checked now.
 *
 * Local teardown after a deletion is not done here — see
 * [com.tenmilelabs.chefai.auth.domain.usecase.DeleteAccountUseCase].
 */
interface AccountRepository {

    /** `GET /api/v1/me/deletion-preview`. */
    suspend fun getDeletionPreview(): AccountDeletionPreviewResult

    /**
     * `POST /api/v1/me/delete`. [publicRecipes] is omitted from the body when `null`, which is
     * what the server expects from a user with no public recipes.
     */
    suspend fun deleteAccount(
        password: String,
        publicRecipes: PublicRecipeDisposition?,
    ): DeleteAccountResult
}
