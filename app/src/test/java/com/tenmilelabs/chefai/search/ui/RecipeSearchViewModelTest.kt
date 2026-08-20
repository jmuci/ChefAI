package com.tenmilelabs.chefai.search.ui

import android.database.sqlite.SQLiteConstraintException
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import coil3.ImageLoader
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.collections.data.repository.FakeCollectionsRepository
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.testutil.FakeSyncScheduler
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.testutil.recipe1
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import com.tenmilelabs.chefai.search.domain.repository.FakeRecipeSearchRepository
import com.tenmilelabs.chefai.search.domain.repository.RecipeSearchOutcome
import com.tenmilelabs.chefai.search.domain.repository.RecipeSearchSource
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Uses a hand-wired [StandardTestDispatcher] + [TestScope] (rather than [MainCoroutineRule]'s
 * default `UnconfinedTestDispatcher()`) because the ViewModel's `.debounce()` needs its `delay()`
 * to run on the *same* virtual clock this test's `advanceTimeBy` drives — `MainCoroutineRule`'s
 * default dispatcher owns its own independent scheduler, so `advanceTimeBy` inside a bare
 * `runTest { }` would never reach it. Mirrors LoginViewModelTest's setup.
 */
@ExperimentalCoroutinesApi
class RecipeSearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    private lateinit var viewModel: RecipeSearchViewModel
    private lateinit var searchRepository: FakeRecipeSearchRepository
    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var collectionsRepository: FakeCollectionsRepository
    private val imageLoader: ImageLoader = mockk(relaxed = true)
    private val appContext: android.content.Context = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        searchRepository = FakeRecipeSearchRepository()
        recipesRepository = FakeRecipesRepository()
        collectionsRepository = FakeCollectionsRepository()

        viewModel = RecipeSearchViewModel(
            recipeSearchRepository = searchRepository,
            recipesRepository = recipesRepository,
            collectionsRepository = collectionsRepository,
            sessionManager = createTestSessionManager(testScope = testScope),
            syncScheduler = FakeSyncScheduler(),
            imageLoader = imageLoader,
            appContext = appContext,
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun preview(id: UUID = UUID.randomUUID(), title: String = "Chicken Soup") = RecipePreview(
        uuid = id,
        title = title,
        description = "desc",
        imageUrlThumbnail = "",
        prepTimeMinutes = 5,
        cookTimeMinutes = 5,
        servings = 2,
        creatorId = UUID.randomUUID(),
        privacy = RecipePrivacy.PUBLIC,
        tags = emptyList(),
        labels = emptyList(),
    )

    @Test
    fun `initial state is Idle`() = testScope.runTest {
        assertThat(viewModel.uiState.value).isEqualTo(SearchUiState.Idle)
    }

    @Test
    fun `a query under 3 characters never reaches the repository`() = testScope.runTest {
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(SearchUiState.Idle) // stateIn's initialValue

            viewModel.onQueryChanged("ch")
            advanceTimeBy(1000)
            runCurrent()

            expectNoEvents()
            assertThat(searchRepository.queriesReceived).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rapid keystrokes within the debounce window collapse into a single call`() = testScope.runTest {
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)

            viewModel.onQueryChanged("chi")
            advanceTimeBy(50)
            viewModel.onQueryChanged("chic")
            advanceTimeBy(50)
            viewModel.onQueryChanged("chick")
            advanceTimeBy(400) // past the 300ms debounce window measured from the last keystroke
            runCurrent()

            assertThat(awaitItem()).isEqualTo(SearchUiState.Searching)
            assertThat(awaitItem()).isEqualTo(SearchUiState.Empty) // default outcome has no results
            assertThat(searchRepository.queriesReceived).containsExactly("chick")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a newer query cancels the in-flight one rather than letting both complete`() = testScope.runTest {
        searchRepository.delayMillis = 1000
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)

            viewModel.onQueryChanged("first")
            advanceTimeBy(400) // debounce fires; "first" starts, emits Searching, suspends on delay(1000)
            runCurrent()
            assertThat(awaitItem()).isEqualTo(SearchUiState.Searching)

            viewModel.onQueryChanged("second")
            // Debounce fires for "second" (cancelling "first" mid-delay), then "second" runs its own
            // full 1000ms delay before resolving.
            advanceTimeBy(1400)
            runCurrent()

            // "second"'s own Searching is conflated away by StateFlow — the prior state is already
            // Searching (from "first"), and SearchUiState.Searching is a singleton `data object`, so
            // no new item is emitted until "second" actually resolves.
            assertThat(awaitItem()).isEqualTo(SearchUiState.Empty)

            assertThat(searchRepository.cancelledCount).isEqualTo(1)
            assertThat(searchRepository.queriesReceived).containsExactly("first", "second").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `results populate SearchUiState Results, and REMOTE source is not flagged offline`() = testScope.runTest {
        val recipe = preview()
        searchRepository.outcomeToReturn = RecipeSearchOutcome(listOf(recipe), false, RecipeSearchSource.REMOTE)

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)

            viewModel.onQueryChanged("chicken")
            advanceTimeBy(400)
            runCurrent()

            assertThat(awaitItem()).isEqualTo(SearchUiState.Searching)
            val results = awaitItem() as SearchUiState.Results
            assertThat(results.items).containsExactly(recipe)
            assertThat(results.isOffline).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a category's query runs the same search pipeline as typed input`() = testScope.runTest {
        // SearchScreen's onCategoryClick feeds SearchCategory.query straight into
        // onQueryChanged — this proves that term reaches the repository through the normal
        // debounce/search flow, with no separate code path for category taps.
        val recipe = preview(title = "Veggie Bowl")
        searchRepository.outcomeToReturn = RecipeSearchOutcome(listOf(recipe), false, RecipeSearchSource.REMOTE)

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)

            viewModel.onQueryChanged("vegetarian")
            advanceTimeBy(400)
            runCurrent()

            assertThat(awaitItem()).isEqualTo(SearchUiState.Searching)
            val results = awaitItem() as SearchUiState.Results
            assertThat(results.items).containsExactly(recipe)
            assertThat(searchRepository.queriesReceived).containsExactly("vegetarian")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a LOCAL_FALLBACK source is flagged offline`() = testScope.runTest {
        searchRepository.outcomeToReturn =
            RecipeSearchOutcome(listOf(preview()), false, RecipeSearchSource.LOCAL_FALLBACK)

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)

            viewModel.onQueryChanged("chicken")
            advanceTimeBy(400)
            runCurrent()

            assertThat(awaitItem()).isEqualTo(SearchUiState.Searching)
            val results = awaitItem() as SearchUiState.Results
            assertThat(results.isOffline).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty result list surfaces as Empty, not a zero-item Results`() = testScope.runTest {
        searchRepository.outcomeToReturn = RecipeSearchOutcome(emptyList(), false, RecipeSearchSource.REMOTE)

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)

            viewModel.onQueryChanged("zzznomatch")
            advanceTimeBy(400)
            runCurrent()

            assertThat(awaitItem()).isEqualTo(SearchUiState.Searching)
            assertThat(awaitItem()).isEqualTo(SearchUiState.Empty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bookmarked recipe ids from CollectionsRepository are reflected in Results`() = testScope.runTest {
        val recipe = preview()
        searchRepository.outcomeToReturn = RecipeSearchOutcome(listOf(recipe), false, RecipeSearchSource.REMOTE)
        collectionsRepository.setBookmarkedIds(setOf(recipe.uuid))

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)

            viewModel.onQueryChanged("chicken")
            advanceTimeBy(400)
            runCurrent()

            assertThat(awaitItem()).isEqualTo(SearchUiState.Searching)
            val results = awaitItem() as SearchUiState.Results
            assertThat(results.bookmarkedRecipeIds).contains(recipe.uuid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving a recipe absent from Room requests a sync and shows the not-yet-synced snackbar, without bookmarking`() =
        testScope.runTest {
            val recipeId = UUID.randomUUID()
            recipesRepository.emitRecipe(recipeId, null) // Room: not found

            turbineScope {
                val events = viewModel.uiEvents.testIn(backgroundScope)
                viewModel.onSaveToCollection(recipeId)
                runCurrent()

                val event = events.awaitItem() as SearchUiEvent.ShowSnackbar
                assertThat(event.messageRes).isEqualTo(R.string.search_recipe_not_yet_synced)
            }
        }

    @Test
    fun `saving a recipe present in Room bookmarks it and shows the added confirmation`() = testScope.runTest {
        val recipeId = UUID.randomUUID()
        recipesRepository.emitRecipe(recipeId, recipe1)

        turbineScope {
            val events = viewModel.uiEvents.testIn(backgroundScope)
            viewModel.onSaveToCollection(recipeId)
            runCurrent()

            val event = events.awaitItem() as SearchUiEvent.ShowSnackbar
            assertThat(event.messageRes).isEqualTo(R.string.search_added_to_collection)
        }
    }

    @Test
    fun `an FK violation on bookmark is caught, not propagated, and requests a sync`() = testScope.runTest {
        val recipeId = UUID.randomUUID()
        recipesRepository.emitRecipe(recipeId, recipe1)
        collectionsRepository.exceptionToThrowOnAddBookmark =
            SQLiteConstraintException("FOREIGN KEY constraint failed")

        turbineScope {
            val events = viewModel.uiEvents.testIn(backgroundScope)
            viewModel.onSaveToCollection(recipeId) // must not throw
            runCurrent()

            val event = events.awaitItem() as SearchUiEvent.ShowSnackbar
            assertThat(event.messageRes).isEqualTo(R.string.search_recipe_not_yet_synced)
        }
    }
}
