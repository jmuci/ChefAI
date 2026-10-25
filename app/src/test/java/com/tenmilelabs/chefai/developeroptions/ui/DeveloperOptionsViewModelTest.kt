package com.tenmilelabs.chefai.developeroptions.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.developeroptions.data.repository.FakeApiEnvironmentRepository
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiBaseUrls
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment
import com.tenmilelabs.chefai.developeroptions.domain.usecase.SwitchApiEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class DeveloperOptionsViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val baseUrls = ApiBaseUrls(local = "http://10.0.2.2:8080", remote = "https://api.example.com")

    private fun viewModel(
        repository: FakeApiEnvironmentRepository,
        baseUrls: ApiBaseUrls = this.baseUrls,
    ) = DeveloperOptionsViewModel(
        apiEnvironmentRepository = repository,
        switchApiEnvironment = SwitchApiEnvironment(
            repository,
            createTestSessionManager(CoroutineScope(mainCoroutineRule.testDispatcher)),
        ),
        apiBaseUrls = baseUrls,
    )

    @Test
    fun `starts on the local environment and shows its URL`() = runTest {
        val viewModel = viewModel(FakeApiEnvironmentRepository())

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isRemote).isFalse()
            assertThat(state.activeBaseUrl).isEqualTo("http://10.0.2.2:8080")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turning remote on stores it and shows the remote URL`() = runTest {
        val repository = FakeApiEnvironmentRepository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertThat(awaitItem().isRemote).isFalse()

            viewModel.onAction(DeveloperOptionsAction.UseRemoteEnvironmentChanged(useRemote = true))

            val state = awaitItem()
            assertThat(state.isRemote).isTrue()
            assertThat(state.activeBaseUrl).isEqualTo("https://api.example.com")
            assertThat(repository.current).isEqualTo(ApiEnvironment.REMOTE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turning remote off goes back to local`() = runTest {
        val repository = FakeApiEnvironmentRepository(ApiEnvironment.REMOTE)
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            // Seeded with the default before the repository flow is collected.
            var latest = awaitItem()
            if (!latest.isRemote) latest = awaitItem()
            assertThat(latest.isRemote).isTrue()

            viewModel.onAction(DeveloperOptionsAction.UseRemoteEnvironmentChanged(useRemote = false))

            assertThat(awaitItem().isRemote).isFalse()
            assertThat(repository.current).isEqualTo(ApiEnvironment.LOCAL)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stored remote choice shows as local when the build has no remote host`() = runTest {
        val unconfigured = baseUrls.copy(remote = "https://api.invalid", remoteConfigured = false)
        val viewModel = viewModel(FakeApiEnvironmentRepository(ApiEnvironment.REMOTE), unconfigured)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isRemote).isFalse()
            assertThat(state.isRemoteConfigured).isFalse()
            assertThat(state.activeBaseUrl).isEqualTo("http://10.0.2.2:8080")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
