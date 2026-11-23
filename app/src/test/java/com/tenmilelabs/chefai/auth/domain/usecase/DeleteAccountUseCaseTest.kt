package com.tenmilelabs.chefai.auth.domain.usecase

import com.tenmilelabs.chefai.auth.domain.model.DeletionProof
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreview
import com.tenmilelabs.chefai.auth.domain.model.DeleteAccountResult
import com.tenmilelabs.chefai.auth.domain.model.PublicRecipeDisposition
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.auth.domain.repository.FakeAccountRepository
import com.tenmilelabs.chefai.core.testutil.createTestSessionManagerWithAuthSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountUseCaseTest {

    private val repository = FakeAccountRepository()
    private lateinit var sessionManager: SessionManager

    private val noPublicRecipes = AccountDeletionPreview(publicRecipeCount = 0, privateRecipeCount = 4, ownsHousehold = false)
    private val twoPublicRecipes = AccountDeletionPreview(publicRecipeCount = 2, privateRecipeCount = 0, ownsHousehold = true)

    private suspend fun TestScope.signedInUseCase(): DeleteAccountUseCase {
        sessionManager = createTestSessionManagerWithAuthSource(backgroundScope).first
        sessionManager.login("cook@example.com", "hunter2")
        return DeleteAccountUseCase(repository, sessionManager, StandardTestDispatcher(testScheduler))
    }

    @Test
    fun `with no public recipes the choice is never sent, even if one was passed`() = runTest {
        val deleteAccount = signedInUseCase()

        deleteAccount(DeletionProof.Password("hunter2"), noPublicRecipes, PublicRecipeDisposition.DELETE)

        assertThat(repository.deleteCalls).containsExactly(DeletionProof.Password("hunter2") to null)
    }

    @Test
    fun `with public recipes and no choice it stops before calling the server`() = runTest {
        val deleteAccount = signedInUseCase()

        val result = deleteAccount(DeletionProof.Password("hunter2"), twoPublicRecipes, publicRecipes = null)

        assertThat(result).isEqualTo(DeleteAccountResult.PublicRecipeChoiceRequired(2))
        assertThat(repository.deleteCalls).isEmpty()
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Authenticated::class.java)
    }

    @Test
    fun `with public recipes the choice is sent`() = runTest {
        val deleteAccount = signedInUseCase()

        deleteAccount(DeletionProof.Password("hunter2"), twoPublicRecipes, PublicRecipeDisposition.ANONYMIZE)

        assertThat(repository.deleteCalls).containsExactly(DeletionProof.Password("hunter2") to PublicRecipeDisposition.ANONYMIZE)
    }

    @Test
    fun `a successful deletion ends the session locally`() = runTest {
        val deleteAccount = signedInUseCase()
        repository.deleteResult = DeleteAccountResult.Deleted

        val result = deleteAccount(DeletionProof.Password("hunter2"), noPublicRecipes, null)

        assertThat(result).isEqualTo(DeleteAccountResult.Deleted)
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Anonymous::class.java)
        assertThat(sessionManager.getAccessToken()).isNull()
    }

    @Test
    fun `a failed deletion keeps the session`() = runTest {
        val deleteAccount = signedInUseCase()
        repository.deleteResult = DeleteAccountResult.WrongPassword

        val result = deleteAccount(DeletionProof.Password("wrong"), noPublicRecipes, null)

        assertThat(result).isEqualTo(DeleteAccountResult.WrongPassword)
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Authenticated::class.java)
    }

    @Test
    fun `a deletion with no answer leaves the wipe marker for the next cold start`() = runTest {
        val deleteAccount = signedInUseCase()
        repository.deleteResult = DeleteAccountResult.Failed

        deleteAccount(DeletionProof.Password("hunter2"), noPublicRecipes, null)

        assertThat(sessionManager.isAccountDeletionPending()).isTrue()
    }

    @Test
    fun `a deletion the server refused clears the wipe marker`() = runTest {
        val deleteAccount = signedInUseCase()
        repository.deleteResult = DeleteAccountResult.WrongPassword

        deleteAccount(DeletionProof.Password("wrong"), noPublicRecipes, null)

        assertThat(sessionManager.isAccountDeletionPending()).isFalse()
    }

    @Test
    fun `a completed deletion clears the wipe marker`() = runTest {
        val deleteAccount = signedInUseCase()
        repository.deleteResult = DeleteAccountResult.Deleted

        deleteAccount(DeletionProof.Password("hunter2"), noPublicRecipes, null)

        assertThat(sessionManager.isAccountDeletionPending()).isFalse()
    }


    @Test
    fun `a Google proof is passed to the repository and a successful deletion ends the session`() = runTest {
        val deleteAccount = signedInUseCase()

        val result = deleteAccount(DeletionProof.GoogleToken("g-token"), noPublicRecipes, null)

        assertThat(result).isEqualTo(DeleteAccountResult.Deleted)
        assertThat(repository.deleteCalls).containsExactly(DeletionProof.GoogleToken("g-token") to null)
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Anonymous::class.java)
    }

    @Test
    fun `with public recipes and no choice a Google proof also stops before calling the server`() = runTest {
        val deleteAccount = signedInUseCase()

        val result = deleteAccount(DeletionProof.GoogleToken("g-token"), twoPublicRecipes, publicRecipes = null)

        assertThat(result).isEqualTo(DeleteAccountResult.PublicRecipeChoiceRequired(2))
        assertThat(repository.deleteCalls).isEmpty()
    }

    @Test
    fun `a rejected Google token keeps the session and clears the wipe marker`() = runTest {
        val deleteAccount = signedInUseCase()
        repository.deleteResult = DeleteAccountResult.WrongPassword

        deleteAccount(DeletionProof.GoogleToken("old"), noPublicRecipes, null)

        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Authenticated::class.java)
        assertThat(sessionManager.isAccountDeletionPending()).isFalse()
    }
}
