package com.tenmilelabs.chefai.auth.data.repository

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.network.AccountDeletionPreviewNetworkResult
import com.tenmilelabs.chefai.auth.data.network.DeleteAccountNetworkResult
import com.tenmilelabs.chefai.auth.data.network.FakeAccountNetworkDataSource
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.data.network.dto.AccountDeletionPreviewResponse
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreview
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreviewResult
import com.tenmilelabs.chefai.auth.domain.model.DeleteAccountResult
import com.tenmilelabs.chefai.auth.domain.model.PublicRecipeDisposition
import com.tenmilelabs.chefai.core.testutil.createTestSessionManagerWithAuthSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAccountRepositoryTest {

    private val network = FakeAccountNetworkDataSource()
    private lateinit var authNetwork: FakeAuthNetworkDataSource
    private lateinit var sessionManager: SessionManager

    private suspend fun TestScope.signedInRepository(): DefaultAccountRepository {
        val (manager, auth) = createTestSessionManagerWithAuthSource(backgroundScope)
        sessionManager = manager
        authNetwork = auth
        sessionManager.login("cook@example.com", "hunter2")
        return DefaultAccountRepository(network, sessionManager)
    }

    private val previewResponse = AccountDeletionPreviewResponse(
        publicRecipeCount = 1,
        privateRecipeCount = 2,
        ownsHousehold = true,
    )

    @Test
    fun `preview maps the response to the domain model`() = runTest {
        val repository = signedInRepository()
        network.previewResults += AccountDeletionPreviewNetworkResult.Success(previewResponse)

        val result = repository.getDeletionPreview()

        assertThat(result).isEqualTo(
            AccountDeletionPreviewResult.Success(
                AccountDeletionPreview(publicRecipeCount = 1, privateRecipeCount = 2, ownsHousehold = true)
            )
        )
    }

    @Test
    fun `preview refreshes once on an expired token and retries`() = runTest {
        val repository = signedInRepository()
        network.previewResults += AccountDeletionPreviewNetworkResult.Unauthenticated
        network.previewResults += AccountDeletionPreviewNetworkResult.Success(previewResponse)

        val result = repository.getDeletionPreview()

        assertThat(result).isInstanceOf(AccountDeletionPreviewResult.Success::class.java)
        assertThat(network.previewCalls).isEqualTo(2)
    }

    @Test
    fun `preview reports SessionExpired when the refresh fails`() = runTest {
        val repository = signedInRepository()
        authNetwork.shouldThrowError = true
        network.previewResults += AccountDeletionPreviewNetworkResult.Unauthenticated

        assertThat(repository.getDeletionPreview()).isEqualTo(AccountDeletionPreviewResult.SessionExpired)
        assertThat(network.previewCalls).isEqualTo(1)
    }

    @Test
    fun `preview offline is Failed`() = runTest {
        val repository = signedInRepository()
        network.errorToThrow = IOException("offline")

        assertThat(repository.getDeletionPreview()).isEqualTo(AccountDeletionPreviewResult.Failed)
    }

    @Test
    fun `delete sends the disposition by name`() = runTest {
        val repository = signedInRepository()
        network.deleteResults += DeleteAccountNetworkResult.Deleted

        val result = repository.deleteAccount("hunter2", PublicRecipeDisposition.DELETE)

        assertThat(result).isEqualTo(DeleteAccountResult.Deleted)
        assertThat(network.deleteCalls).containsExactly("hunter2" to "DELETE")
    }

    @Test
    fun `a wrong password is returned as is, without refreshing the token or retrying`() = runTest {
        val repository = signedInRepository()
        // A refresh would throw here, so a wrong password that triggered one would surface as
        // SessionExpired instead.
        authNetwork.shouldThrowError = true
        network.deleteResults += DeleteAccountNetworkResult.WrongPassword

        val result = repository.deleteAccount("wrong", null)

        assertThat(result).isEqualTo(DeleteAccountResult.WrongPassword)
        assertThat(network.deleteCalls).hasSize(1)
    }

    @Test
    fun `delete refreshes once on an expired token and retries with the same body`() = runTest {
        val repository = signedInRepository()
        network.deleteResults += DeleteAccountNetworkResult.Unauthenticated
        network.deleteResults += DeleteAccountNetworkResult.Deleted

        val result = repository.deleteAccount("hunter2", PublicRecipeDisposition.ANONYMIZE)

        assertThat(result).isEqualTo(DeleteAccountResult.Deleted)
        assertThat(network.deleteCalls).containsExactly(
            "hunter2" to "ANONYMIZE",
            "hunter2" to "ANONYMIZE",
        ).inOrder()
    }

    @Test
    fun `delete still unauthenticated after a refresh is SessionExpired, never a loop`() = runTest {
        val repository = signedInRepository()
        network.deleteResults += DeleteAccountNetworkResult.Unauthenticated

        assertThat(repository.deleteAccount("hunter2", null)).isEqualTo(DeleteAccountResult.SessionExpired)
        assertThat(network.deleteCalls).hasSize(2)
    }

    @Test
    fun `delete maps each server outcome`() = runTest {
        val repository = signedInRepository()
        val cases = mapOf(
            DeleteAccountNetworkResult.PublicRecipeChoiceRequired(3) to DeleteAccountResult.PublicRecipeChoiceRequired(3),
            DeleteAccountNetworkResult.Conflict to DeleteAccountResult.Conflict,
            DeleteAccountNetworkResult.RateLimited to DeleteAccountResult.RateLimited,
            DeleteAccountNetworkResult.Failed(500) to DeleteAccountResult.Failed,
        )
        for ((networkResult, expected) in cases) {
            network.deleteResults.clear()
            network.deleteResults += networkResult
            assertThat(repository.deleteAccount("hunter2", null)).isEqualTo(expected)
        }
    }

    @Test
    fun `delete offline is Failed`() = runTest {
        val repository = signedInRepository()
        network.errorToThrow = IOException("offline")

        assertThat(repository.deleteAccount("hunter2", null)).isEqualTo(DeleteAccountResult.Failed)
    }
}
