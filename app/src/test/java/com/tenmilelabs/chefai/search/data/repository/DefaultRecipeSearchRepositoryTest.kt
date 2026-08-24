package com.tenmilelabs.chefai.search.data.repository

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.AuthToken
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.core.testutil.createTestSessionManagerWithAuthSource
import com.tenmilelabs.chefai.search.data.network.RecipeSearchNetworkDataSource
import com.tenmilelabs.chefai.search.data.network.RecipeSearchNetworkResult
import com.tenmilelabs.chefai.search.data.network.dto.RecipeSearchResponseDto
import com.tenmilelabs.chefai.search.data.network.dto.RecipeSearchResultDto
import com.tenmilelabs.chefai.search.domain.repository.RecipeSearchSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class DefaultRecipeSearchRepositoryTest {

    private val fakeRecipeDao = FakeRecipeDao()
    private val fakeApiService = mockk<RecipeSearchNetworkDataSource>()
    private val sessionManager = mockk<SessionManager>()

    private fun repository() = DefaultRecipeSearchRepository(fakeApiService, fakeRecipeDao, sessionManager)

    private fun anonymousSession() {
        every { sessionManager.userSession } returns MutableStateFlow(UserSession.Anonymous(UUID.randomUUID()))
    }

    private fun authenticatedSession() {
        every { sessionManager.userSession } returns MutableStateFlow(
            UserSession.Authenticated(
                user = User(UUID.randomUUID(), "Test", "t@example.com", ""),
                authToken = AuthToken("token", "refresh", Long.MAX_VALUE),
            )
        )
    }

    private fun seedLocalRecipe(title: String): UUID {
        val recipeId = UUID.randomUUID()
        val creatorId = UUID.randomUUID()
        fakeRecipeDao.seed(
            users = listOf(UserEntity(creatorId, "Owner", "o@example.com", "", 0L, null, SyncState.SYNCED)),
            recipes = listOf(
                RecipeEntity(
                    uuid = recipeId,
                    title = title,
                    description = "",
                    imageUrl = "",
                    imageUrlThumbnail = "",
                    prepTimeMinutes = 5,
                    cookTimeMinutes = 5,
                    servings = 1,
                    creatorId = creatorId,
                    recipeExternalUrl = null,
                    privacy = RecipePrivacy.PUBLIC,
                    updatedAt = 0L,
                    deletedAt = null,
                    syncState = SyncState.SYNCED,
                )
            ),
        )
        return recipeId
    }

    private fun remoteResult(title: String = "Remote Chicken") = RecipeSearchNetworkResult.Success(
        RecipeSearchResponseDto(
            query = "chicken",
            results = listOf(
                RecipeSearchResultDto(
                    uuid = UUID.randomUUID().toString(),
                    title = title,
                    description = "",
                    imageUrl = "",
                    imageUrlThumbnail = "",
                    prepTimeMinutes = 5,
                    cookTimeMinutes = 5,
                    servings = 1,
                    creatorId = UUID.randomUUID().toString(),
                    privacy = "PUBLIC",
                    updatedAt = 0L,
                    tags = emptyList(),
                    labels = emptyList(),
                )
            ),
            hasMore = false,
        )
    )

    @Test
    fun `an anonymous session hits the network and returns the remote catalog`() = runTest {
        // ChefAI#184: this used to short-circuit to the on-device scan, capping anonymous search
        // at whatever the device had already synced. The backend serves anonymous callers the
        // public catalog, so the round trip is worth making.
        anonymousSession()
        seedLocalRecipe("Chicken Soup")
        coEvery { fakeApiService.search("chicken", 20, 0) } returns remoteResult()

        val outcome = repository().search("chicken", 20, 0)

        assertThat(outcome.source).isEqualTo(RecipeSearchSource.REMOTE)
        assertThat(outcome.results.single().title).isEqualTo("Remote Chicken")
        coVerify(exactly = 1) { fakeApiService.search("chicken", 20, 0) }
    }

    @Test
    fun `an anonymous session still falls back locally when the network fails`() = runTest {
        anonymousSession()
        coEvery { fakeApiService.search(any(), any(), any()) } returns RecipeSearchNetworkResult.Error("boom")
        val recipeId = seedLocalRecipe("Chicken Soup")

        val outcome = repository().search("chicken", 20, 0)

        assertThat(outcome.source).isEqualTo(RecipeSearchSource.LOCAL_FALLBACK)
        assertThat(outcome.results.map { it.uuid }).containsExactly(recipeId)
    }

    @Test
    fun `an anonymous Unauthorized falls back without attempting a pointless token refresh`() = runTest {
        // Only reachable against a backend that predates ChefAI#184. An anonymous session has no
        // refresh token, so refreshing here would burn a round trip on every keystroke to fail.
        anonymousSession()
        coEvery { fakeApiService.search(any(), any(), any()) } returns RecipeSearchNetworkResult.Unauthorized
        val recipeId = seedLocalRecipe("Chicken Soup")

        val outcome = repository().search("chicken", 20, 0)

        assertThat(outcome.source).isEqualTo(RecipeSearchSource.LOCAL_FALLBACK)
        assertThat(outcome.results.map { it.uuid }).containsExactly(recipeId)
        coVerify(exactly = 0) { sessionManager.refreshToken() }
        coVerify(exactly = 1) { fakeApiService.search(any(), any(), any()) }
    }

    @Test
    fun `a successful remote response is mapped and marked REMOTE`() = runTest {
        authenticatedSession()
        coEvery { fakeApiService.search("chicken", 20, 0) } returns remoteResult()

        val outcome = repository().search("chicken", 20, 0)

        assertThat(outcome.source).isEqualTo(RecipeSearchSource.REMOTE)
        assertThat(outcome.results.single().title).isEqualTo("Remote Chicken")
    }

    @Test
    fun `a network Error falls back to the local search`() = runTest {
        authenticatedSession()
        coEvery { fakeApiService.search(any(), any(), any()) } returns RecipeSearchNetworkResult.Error("boom")
        val recipeId = seedLocalRecipe("Chicken Soup")

        val outcome = repository().search("chicken", 20, 0)

        assertThat(outcome.source).isEqualTo(RecipeSearchSource.LOCAL_FALLBACK)
        assertThat(outcome.results.map { it.uuid }).containsExactly(recipeId)
    }

    @Test
    fun `Unauthorized triggers exactly one refresh-then-retry, and a successful retry is REMOTE`() = runTest {
        authenticatedSession()
        coEvery { sessionManager.refreshToken() } returns Result.success(Unit)
        coEvery { fakeApiService.search(any(), any(), any()) } returnsMany listOf(
            RecipeSearchNetworkResult.Unauthorized,
            remoteResult("Retried Chicken"),
        )

        val outcome = repository().search("chicken", 20, 0)

        assertThat(outcome.source).isEqualTo(RecipeSearchSource.REMOTE)
        assertThat(outcome.results.single().title).isEqualTo("Retried Chicken")
        coVerify(exactly = 1) { sessionManager.refreshToken() }
        coVerify(exactly = 2) { fakeApiService.search(any(), any(), any()) }
    }

    @Test
    fun `Unauthorized with a failed refresh falls back to local without retrying the network`() = runTest {
        // Goes through a real SessionManager (login for real, then make the underlying fake auth
        // endpoint throw) rather than mocking SessionManager.refreshToken() directly — MockK does
        // not reliably preserve Result.failure across its suspend-function proxy for a function
        // whose return type is itself kotlin.Result (a known MockK/Kotlin inline-class limitation,
        // confirmed empirically here, not a claim about production behavior: the real
        // SessionManager.refreshToken() never throws, it always returns a Result).
        val (realSessionManager, fakeAuthNetworkDataSource) = createTestSessionManagerWithAuthSource(this)
        val loginResult = realSessionManager.login("t@example.com", "password")
        check(loginResult.isSuccess) { "test setup: login must succeed before simulating a refresh failure" }
        fakeAuthNetworkDataSource.shouldThrowError = true

        coEvery { fakeApiService.search(any(), any(), any()) } returns RecipeSearchNetworkResult.Unauthorized
        val recipeId = seedLocalRecipe("Chicken Soup")

        val outcome = DefaultRecipeSearchRepository(fakeApiService, fakeRecipeDao, realSessionManager)
            .search("chicken", 20, 0)

        assertThat(outcome.source).isEqualTo(RecipeSearchSource.LOCAL_FALLBACK)
        assertThat(outcome.results.map { it.uuid }).containsExactly(recipeId)
        coVerify(exactly = 1) { fakeApiService.search(any(), any(), any()) }
    }

    @Test
    fun `Unauthorized twice in a row — a successful refresh but a still-Unauthorized retry — falls back to local, not a loop`() =
        runTest {
            authenticatedSession()
            coEvery { sessionManager.refreshToken() } returns Result.success(Unit)
            coEvery { fakeApiService.search(any(), any(), any()) } returns RecipeSearchNetworkResult.Unauthorized
            val recipeId = seedLocalRecipe("Chicken Soup")

            val outcome = repository().search("chicken", 20, 0)

            assertThat(outcome.source).isEqualTo(RecipeSearchSource.LOCAL_FALLBACK)
            assertThat(outcome.results.map { it.uuid }).containsExactly(recipeId)
            coVerify(exactly = 1) { sessionManager.refreshToken() }
            coVerify(exactly = 2) { fakeApiService.search(any(), any(), any()) }
        }
}
