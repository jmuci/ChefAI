package com.tenmilelabs.chefai.recipes.ui


import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import com.tenmilelabs.chefai.core.testutil.TEST_DOMAIN_RECIPE_PREVIEWS_LIST
import com.tenmilelabs.chefai.core.testutil.recipePreview1
import com.tenmilelabs.chefai.core.testutil.recipePreview2
import com.tenmilelabs.chefai.core.testutil.recipePreview3
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
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

    @Before
    fun setup() {
        recipesRepository = FakeRecipesRepository()
    }

    private fun initializeViewModel() {
        // ViewModel starts collecting on init
        viewModel = RecipesViewModel(recipesRepository)
    }

    @Test
    fun `initial state is loading when no data emitted yet`() = runTest {
        // Don't emit any data initially
        initializeViewModel()

        viewModel.uiState.test {
            // Expect initial state from stateIn (loading=true, empty items)
            val initialState = awaitItem()
            assertThat(initialState.isLoading).isTrue()
            assertThat(initialState.items).isEmpty()
            assertThat(initialState.userMessage).isNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState reflects emitted recipes with success message`() = runTest {
        // Emit data before initializing ViewModel (since MutableSharedFlow has replay=1)
        recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)

        initializeViewModel()

        viewModel.uiState.test {
            // Since repository already has data with replay=1, first emission should be success state
            val successState = awaitItem()
            assertThat(successState.isLoading).isFalse()
            assertThat(successState.items).isEqualTo(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)
            assertThat(successState.items).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState reflects empty list when repository emits empty data`() = runTest {
        // Emit empty list before initializing ViewModel
        recipesRepository.setRecipePreviewsToEmit(emptyList())

        initializeViewModel()

        viewModel.uiState.test {
            // Success state with empty items
            val emptyState = awaitItem()
            assertThat(emptyState.isLoading).isFalse()
            assertThat(emptyState.items).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState shows error message when repository throws error`() = runTest {
        // Configure repository to throw error
        recipesRepository.setShouldReturnErrorForGetRecipes(true)

        initializeViewModel()

        viewModel.uiState.test {
            // Expect error state with error message
            val errorState = awaitItem()
            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.items).isEmpty()
            assertThat(errorState.userMessage).isEqualTo(R.string.loading_recipes_error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `snackbarMessageShown does not affect state in current implementation`() = runTest {
        // Note: snackbarMessageShown() sets _userMessage but it's not actually used in combine
        // The userMessage in the state is hardcoded based on Success/Error states
        recipesRepository.setShouldReturnErrorForGetRecipes(true)

        initializeViewModel()

        viewModel.uiState.test {
            val errorState = awaitItem()
            assertThat(errorState.userMessage).isEqualTo(R.string.loading_recipes_error)

            // Call snackbarMessageShown
            viewModel.snackbarMessageShown()

            // The message should not change since _userMessage is not actually used in the combine
            // This test documents the current behavior
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState updates when repository emits new data after initialization`() = runTest {
        // Start with empty data
        recipesRepository.setRecipePreviewsToEmit(emptyList())

        initializeViewModel()

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

        initializeViewModel()

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
        recipesRepository.setShouldReturnErrorForGetRecipes(true)

        initializeViewModel()

        viewModel.uiState.test {
            val errorState = awaitItem()

            // Verify error state structure
            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.items).isEmpty()
            assertThat(errorState.userMessage).isNotNull()
            assertThat(errorState.userMessage).isEqualTo(R.string.loading_recipes_error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoading is false after successful data load`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)

        initializeViewModel()

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
        val errorViewModel = RecipesViewModel(recipesRepository)

        errorViewModel.uiState.test {
            val errorState = awaitItem()
            assertThat(errorState.userMessage).isEqualTo(R.string.loading_recipes_error)
            assertThat(errorState.items).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }

        // Second test: success state (with new repository/viewmodel instance)
        val successRepository = FakeRecipesRepository()
        successRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)
        val successViewModel = RecipesViewModel(successRepository)

        successViewModel.uiState.test {
            val successState = awaitItem()
            assertThat(successState.items).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState handles single recipe emission`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1))

        initializeViewModel()

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

        initializeViewModel()

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
        // Don't pre-emit any data
        initializeViewModel()

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.isLoading).isTrue()
            assertThat(initialState.items).isEmpty()
            assertThat(initialState.userMessage).isNull()

            // Now emit data
            recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)

            val loadedState = awaitItem()
            assertThat(loadedState.isLoading).isFalse()
            assertThat(loadedState.items).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState handles multiple rapid updates`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(emptyList())

        initializeViewModel()

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

        initializeViewModel()

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
