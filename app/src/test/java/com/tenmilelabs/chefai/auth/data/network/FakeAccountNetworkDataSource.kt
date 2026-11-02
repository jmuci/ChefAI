package com.tenmilelabs.chefai.auth.data.network

/** Queue-driven: each call takes the next result, and repeats the last one once the queue is empty. */
class FakeAccountNetworkDataSource : AccountNetworkDataSource {

    val previewResults = ArrayDeque<AccountDeletionPreviewNetworkResult>()
    val deleteResults = ArrayDeque<DeleteAccountNetworkResult>()

    /** Thrown by every call instead of returning, to simulate going offline. */
    var errorToThrow: Exception? = null

    var previewCalls = 0
        private set
    val deleteCalls = mutableListOf<Pair<String, String?>>()

    override suspend fun getDeletionPreview(): AccountDeletionPreviewNetworkResult {
        previewCalls++
        errorToThrow?.let { throw it }
        return if (previewResults.size > 1) previewResults.removeFirst() else previewResults.first()
    }

    override suspend fun deleteAccount(password: String, publicRecipes: String?): DeleteAccountNetworkResult {
        deleteCalls += password to publicRecipes
        errorToThrow?.let { throw it }
        return if (deleteResults.size > 1) deleteResults.removeFirst() else deleteResults.first()
    }
}
