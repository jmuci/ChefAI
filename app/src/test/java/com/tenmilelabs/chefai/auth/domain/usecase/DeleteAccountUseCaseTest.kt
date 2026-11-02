package com.tenmilelabs.chefai.auth.domain.usecase

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

        deleteAccount("hunter2", noPublicRecipes, PublicRecipeDisposition.DELETE)

        assertThat(repository.deleteCalls).containsExactly("hunter2" to null)
    }

    @Test
    fun `with public recipes and no choice it stops before calling the server`() = runTest {
        val deleteAccount = signedInUseCase()

        val result = deleteAccount("hunter2", twoPublicRecipes, publicRecipes = null)

        assertThat(result).isEqualTo(DeleteAccountResult.PublicRecipeChoiceRequired(2))
        assertThat(repository.deleteCalls).isEmpty()
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Authenticated::class.java)
    }

    @Test
    fun `with public recipes the choice is sent`() = runTest {
        val deleteAccount = signedInUseCase()

        deleteAccount("hunter2", twoPublicRecipes, PublicRecipeDisposition.ANONYMIZE)

        assertThat(repository.deleteCalls).containsExactly("hunter2" to PublicRecipeDisposition.ANONYMIZE)
    }

    @Test
    fun `a successful deletion ends the session locally`() = runTest {
        val deleteAccount = signedInUseCase()
        repository.deleteResult = DeleteAccountResult.Deleted

        val result = deleteAccount("hunter2", noPublicRecipes, null)

        assertThat(result).isEqualTo(DeleteAccountResult.Deleted)
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Anonymous::class.java)
        assertThat(sessionManager.getAccessToken()).isNull()
    }

    @Test
    fun `a failed deletion keeps the session`() = runTest {
        val deleteAccount = signedInUseCase()
        repository.deleteResult = DeleteAccountResult.WrongPassword

        val result = deleteAccount("wrong", noPublicRecipes, null)

        assertThat(result).isEqualTo(DeleteAccountResult.WrongPassword)
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Authenticated::class.java)
    }
}
