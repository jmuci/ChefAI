package com.tenmilelabs.chefai.developeroptions.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.developeroptions.data.repository.FakeApiEnvironmentRepository
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class SwitchApiEnvironmentTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val repository = FakeApiEnvironmentRepository()

    // Lazy: the rule only installs its Main dispatcher once the test starts, after field init.
    private val sessionManager by lazy {
        createTestSessionManager(CoroutineScope(mainCoroutineRule.testDispatcher))
    }
    private val switchApiEnvironment by lazy { SwitchApiEnvironment(repository, sessionManager) }

    @Test
    fun `switching while signed in signs out and then switches`() = runTest {
        // Given: a signed-in account on the local environment
        sessionManager.login("test@example.com", "password123")
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Authenticated::class.java)

        // When: switching to remote
        switchApiEnvironment(ApiEnvironment.REMOTE)

        // Then: the account is signed out and the app points at remote
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Anonymous::class.java)
        assertThat(sessionManager.getAccessToken()).isNull()
        assertThat(repository.current).isEqualTo(ApiEnvironment.REMOTE)
    }

    @Test
    fun `switching while anonymous keeps the same anonymous session`() = runTest {
        // Given: an anonymous session
        val before = sessionManager.userSession.value
        assertThat(before).isInstanceOf(UserSession.Anonymous::class.java)

        // When: switching to remote
        switchApiEnvironment(ApiEnvironment.REMOTE)

        // Then: the session is untouched
        assertThat(sessionManager.userSession.value).isEqualTo(before)
        assertThat(repository.current).isEqualTo(ApiEnvironment.REMOTE)
    }

    @Test
    fun `choosing the environment already in use does not sign out`() = runTest {
        // Given: a signed-in account on the local environment
        sessionManager.login("test@example.com", "password123")

        // When: "switching" to local again
        switchApiEnvironment(ApiEnvironment.LOCAL)

        // Then: still signed in
        assertThat(sessionManager.userSession.value).isInstanceOf(UserSession.Authenticated::class.java)
        assertThat(repository.current).isEqualTo(ApiEnvironment.LOCAL)
    }
}
