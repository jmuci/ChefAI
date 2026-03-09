package com.tenmilelabs.chefai.recipes.ui


import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import com.tenmilelabs.chefai.core.data.sync.SyncStatusHolder
import com.tenmilelabs.chefai.core.testutil.TEST_DOMAIN_RECIPE_PREVIEWS_LIST
import com.tenmilelabs.chefai.core.testutil.createRealSessionManagerWithFakes
import com.tenmilelabs.chefai.core.testutil.recipePreview1
import com.tenmilelabs.chefai.core.testutil.recipePreview2
import com.tenmilelabs.chefai.core.testutil.recipePreview3
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for RecipesViewModel.
 * Tests cover all functionality including state management, error handling, and user interactions.
 */
@ExperimentalCoroutinesApi
class RecipesViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: RecipesViewModel
    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var fakeSyncManager: FakeSyncManager
    private lateinit var syncStatusHolder: SyncStatusHolder

    @Before
    fun setup() {
        recipesRepository = FakeRecipesRepository()
        sessionManager = createRealSessionManagerWithFakes()
        fakeSyncManager = FakeSyncManager()
        syncStatusHolder = SyncStatusHolder()

        viewModel = RecipesViewModel(recipesRepository, sessionManager, fakeSyncManager, syncStatusHolder)
    }

    @Test
    fun `initial state is loading when no data emitted yet`() = runTest {
        viewModel.uiState.test {
            // Expect initial state from stateIn (loading=true, empty items)
            val initialState = awaitItem()
            assertThat(initialState.isLoading).isTrue()
            assertThat(initialState.items).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState reflects emitted recipes with success message`() = runTest {
        // Emit data (since ViewModel is already initialized, new data triggers updates)
        recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)

        viewModel.uiState.test {
            val successState = awaitItem()
            assertThat(successState.isLoading).isFalse()
            assertThat(successState.items).isEqualTo(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)
            assertThat(successState.items).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState reflects empty list when repository emits empty data`() = runTest {
        // Emit empty list
        recipesRepository.setRecipePreviewsToEmit(emptyList())

        viewModel.uiState.test {
            // Success state with empty items
            val emptyState = awaitItem()
            assertThat(emptyState.isLoading).isFalse()
            assertThat(emptyState.items).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState shows error and emits ShowSnackbar event when repository throws error`() = runTest {
        // Need to create a new ViewModel for this test with error configuration
        val errorRepository = FakeRecipesRepository()
        errorRepository.setShouldReturnErrorForGetRecipes(true)
        val testViewModel = RecipesViewModel(errorRepository, sessionManager, fakeSyncManager, syncStatusHolder)

        // Collect and verify UI state
        testViewModel.uiState.test {
            val errorState = awaitItem()
            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.items).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

        // Verify that a ShowSnackbar event is emitted (with replay=1, it should be available)
        testViewModel.uiEvents.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(RecipesUiEvent.ShowSnackbar::class.java)
            assertThat((event as RecipesUiEvent.ShowSnackbar).message)
                .isEqualTo(R.string.loading_recipes_error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiEvents emits ShowSnackbar only once per error`() = runTest {
        // Need a new ViewModel for error scenario
        val errorRepository = FakeRecipesRepository()
        errorRepository.setShouldReturnErrorForGetRecipes(true)
        val testViewModel = RecipesViewModel(errorRepository, sessionManager, fakeSyncManager, syncStatusHolder)

        // Trigger uiState collection to ensure the flow starts
        testViewModel.uiState.test {
            val errorState = awaitItem()
            assertThat(errorState.isLoading).isFalse()
            cancelAndIgnoreRemainingEvents()
        }

        // Now collect events
        testViewModel.uiEvents.test {
            // Wait for first ShowSnackbar event
            val firstEvent = awaitItem()
            assertThat(firstEvent).isInstanceOf(RecipesUiEvent.ShowSnackbar::class.java)

            // No more events should be emitted without additional errors
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState updates when repository emits new data after initialization`() = runTest {
        // Start with empty data
        recipesRepository.setRecipePreviewsToEmit(emptyList())

        viewModel.uiState.test {
            // First state - empty
            val firstState = awaitItem()
            assertThat(firstState.items).isEmpty()

            // Emit single recipe
            recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1))

            val secondState = awaitItem()
            assertThat(secondState.items).hasSize(1)
            assertThat(secondState.items[0]).isEqualTo(recipePreview1)

            // Emit multiple recipes
            recipesRepository.setRecipePreviewsToEmit(
                listOf(
                    recipePreview1,
                    recipePreview2,
                    recipePreview3
                )
            )

            val thirdState = awaitItem()
            assertThat(thirdState.items).hasSize(3)
            assertThat(thirdState.items).containsExactly(
                recipePreview1,
                recipePreview2,
                recipePreview3
            ).inOrder()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState contains correct recipe data`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1, recipePreview2))

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.items).hasSize(2)

            // Verify first recipe
            val firstRecipe = state.items[0]
            assertThat(firstRecipe.uuid).isEqualTo(recipePreview1.uuid)
            assertThat(firstRecipe.title).isEqualTo("Pancakes")
            assertThat(firstRecipe.description).isEqualTo("Fluffy American-style pancakes.")

            // Verify second recipe
            val secondRecipe = state.items[1]
            assertThat(secondRecipe.uuid).isEqualTo(recipePreview2.uuid)
            assertThat(secondRecipe.title).isEqualTo("French Toast")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error state maintains empty items list`() = runTest {
        // Need a new ViewModel for error scenario
        val errorRepository = FakeRecipesRepository()
        errorRepository.setShouldReturnErrorForGetRecipes(true)
        val testViewModel = RecipesViewModel(errorRepository, sessionManager, fakeSyncManager, syncStatusHolder)

        testViewModel.uiState.test {
            val errorState = awaitItem()

            // Verify error state structure
            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.items).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }

        // Verify corresponding event is emitted
        testViewModel.uiEvents.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(RecipesUiEvent.ShowSnackbar::class.java)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoading is false after successful data load`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)

        viewModel.uiState.test {
            // First emission should be success with isLoading=false
            val loadedState = awaitItem()
            assertThat(loadedState.isLoading).isFalse()
            assertThat(loadedState.items).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState handles transition from error to success`() = runTest {
        // Note: This test verifies error handling behavior
        // In a real app, recovering from an error state would require recreating the ViewModel
        // or implementing a retry mechanism. For now, we test that both states work independently.

        // First test: error state
        recipesRepository.setShouldReturnErrorForGetRecipes(true)
        val errorViewModel = RecipesViewModel(recipesRepository, sessionManager, fakeSyncManager, syncStatusHolder)

        errorViewModel.uiState.test {
            val errorState = awaitItem()
            assertThat(errorState.items).isEmpty()
            assertThat(errorState.isLoading).isFalse()

            cancelAndIgnoreRemainingEvents()
        }

        errorViewModel.uiEvents.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(RecipesUiEvent.ShowSnackbar::class.java)

            cancelAndIgnoreRemainingEvents()
        }

        // Second test: success state (with new repository/viewmodel instance)
        val successRepository = FakeRecipesRepository()
        successRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)
        val successViewModel = RecipesViewModel(successRepository, sessionManager, fakeSyncManager, syncStatusHolder)

        successViewModel.uiState.test {
            val successState = awaitItem()
            assertThat(successState.items).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }

        // No events should be emitted in success case
        successViewModel.uiEvents.test {
            expectNoEvents()
        }
    }

    @Test
    fun `uiState handles single recipe emission`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1))

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.items).hasSize(1)
            assertThat(state.items[0].title).isEqualTo("Pancakes")
            assertThat(state.items[0].prepTimeMinutes).isEqualTo(10)
            assertThat(state.items[0].cookTimeMinutes).isEqualTo(15)
            assertThat(state.items[0].servings).isEqualTo(4)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState preserves recipe order from repository`() = runTest {
        // Emit recipes in specific order
        val orderedPreviews = listOf(recipePreview3, recipePreview1, recipePreview2)
        recipesRepository.setRecipePreviewsToEmit(orderedPreviews)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.items).hasSize(3)
            assertThat(state.items[0].title).isEqualTo("Omelette")
            assertThat(state.items[1].title).isEqualTo("Pancakes")
            assertThat(state.items[2].title).isEqualTo("French Toast")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial loading state is shown when data not yet emitted`() = runTest {
        // Don't emit any data
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.isLoading).isTrue()
            assertThat(initialState.items).isEmpty()

            // Now emit data
            recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)

            val loadedState = awaitItem()
            assertThat(loadedState.isLoading).isFalse()
            assertThat(loadedState.items).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }

        // No events should be emitted in success case
        viewModel.uiEvents.test {
            expectNoEvents()
        }
    }

    @Test
    fun `uiState handles multiple rapid updates`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(emptyList())

        viewModel.uiState.test {
            // First state - empty
            assertThat(awaitItem().items).isEmpty()

            // Rapid updates
            recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1))
            assertThat(awaitItem().items).hasSize(1)

            recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1, recipePreview2))
            assertThat(awaitItem().items).hasSize(2)

            recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)
            assertThat(awaitItem().items).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recipe preview contains all required fields`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1))

        viewModel.uiState.test {
            val state = awaitItem()
            val recipe = state.items[0]

            // Verify all fields are populated
            assertThat(recipe.uuid).isNotNull()
            assertThat(recipe.title).isNotEmpty()
            assertThat(recipe.description).isNotEmpty()
            assertThat(recipe.prepTimeMinutes).isAtLeast(0)
            assertThat(recipe.cookTimeMinutes).isAtLeast(0)
            assertThat(recipe.servings).isAtLeast(0)
            assertThat(recipe.creatorId).isNotNull()
            assertThat(recipe.tags).isNotNull()
            assertThat(recipe.labels).isNotNull()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
