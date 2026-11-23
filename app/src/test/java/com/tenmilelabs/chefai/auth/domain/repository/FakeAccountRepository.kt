package com.tenmilelabs.chefai.auth.domain.repository

import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreview
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreviewResult
import com.tenmilelabs.chefai.auth.domain.model.DeleteAccountResult
import com.tenmilelabs.chefai.auth.domain.model.DeletionProof
import com.tenmilelabs.chefai.auth.domain.model.PublicRecipeDisposition
import kotlinx.coroutines.CompletableDeferred

class FakeAccountRepository(
    var previewResult: AccountDeletionPreviewResult = AccountDeletionPreviewResult.Success(
        AccountDeletionPreview(publicRecipeCount = 0, privateRecipeCount = 0, ownsHousehold = false)
    ),
    var deleteResult: DeleteAccountResult = DeleteAccountResult.Deleted,
) : AccountRepository {

    val deleteCalls = mutableListOf<Pair<DeletionProof, PublicRecipeDisposition?>>()

    /** When set, [deleteAccount] suspends until it completes — for asserting the in-flight state. */
    var deleteGate: CompletableDeferred<Unit>? = null

    override suspend fun getDeletionPreview(): AccountDeletionPreviewResult = previewResult

    override suspend fun deleteAccount(
        proof: DeletionProof,
        publicRecipes: PublicRecipeDisposition?,
    ): DeleteAccountResult {
        deleteCalls += proof to publicRecipes
        deleteGate?.await()
        return deleteResult
    }
}
