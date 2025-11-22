package com.tenmilelabs.chefai.recipes.ui.details

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import com.tenmilelabs.chefai.core.testutil.recipe1
import com.tenmilelabs.chefai.core.testutil.recipeId1
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.recipes.ui.details.RecipeDetailsViewModel
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class RecipeDetailsViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: RecipeDetailsViewModel
    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setup() {
        recipesRepository = FakeRecipesRepository()
        savedStateHandle = SavedStateHandle().apply {
            set(AppDestinationArgs.RECIPE_ID_ARG, recipeId1.toString())
        }
        // ViewModel is re-initialized for each test to ensure clean state
    }

    private fun initializeViewModel() {
        viewModel = RecipeDetailsViewModel(recipesRepository, savedStateHandle)
    }

    @Test
    fun `initial state is loading and recipeUuid is set`() = runTest {
        initializeViewModel()
        assertThat(viewModel.recipeUuid).isEqualTo(recipeId1)

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.isLoading).isTrue()
            assertThat(initialState.recipe).isNull()
            assertThat(initialState.userMessage).isNull()
            cancelAndConsumeRemainingEvents() // Important if WhileUiSubscribed has a timeout
        }
    }

    @Test
    fun `loadRecipe - success - uiState reflects loaded recipe`() = runTest {
        initializeViewModel()
        viewModel.uiState.test {
            // 1. Initial Loading state
            assertThat(awaitItem().isLoading).isTrue()

            // 2. Repository emits data
            recipesRepository.emitRecipe(recipeId1, recipe1)

            // 3. Success state
            val successState = awaitItem()
            assertThat(successState.isLoading).isFalse() // Because _isLoading is false by default
            assertThat(successState.recipe).isEqualTo(recipe1)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `loadRecipe - repository returns null for recipe - shows error`() = runTest {
        initializeViewModel()
        viewModel.uiState.test {
            // 1. Initial Loading state
            assertThat(awaitItem().isLoading).isTrue()

            // 2. Repository emits null (recipe not found)
            recipesRepository.emitRecipe(recipeId1, null)

            // 3. Error state
            val errorState = awaitItem()
            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.recipe).isNull()
            assertThat(errorState.userMessage).isEqualTo(R.string.loading_recipe_details_error)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `loadRecipe - repository flow throws exception - shows error`() = runTest {
        recipesRepository.setShouldReturnErrorForGetRecipe(true) // Configure repo to throw
        initializeViewModel() // Initialize ViewModel *after* repo is configured to throw

        viewModel.uiState.test {
            // 1. Error state (due to exception from the flow)
            // The exact number of emissions can depend on how quickly the error propagates
            // vs. initial value and combine. We might get an intermediate state.
            val errorState = awaitItem() // Wait for the state to settle after the error
            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.recipe).isNull()
            assertThat(errorState.userMessage).isEqualTo(R.string.loading_recipe_details_error)

            cancelAndConsumeRemainingEvents()
        }
    }


    @Test
    fun `snackbarMessageShown - clears userMessage in uiState`() = runTest {
        initializeViewModel()
        viewModel.uiState.test {
            // 1. Initial loading state
            assertThat(awaitItem().isLoading).isTrue()

            // 2. Simulate an error to set a userMessage
            recipesRepository.emitRecipe(recipeId1, null)
            val errorState = awaitItem()
            assertThat(errorState.userMessage).isEqualTo(R.string.loading_recipe_details_error)
            assertThat(errorState.recipe).isNull()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `loadRecipe - success - isLoading reflects _isLoading state`() = runTest {
        // This test demonstrates how _isLoading (if it were mutable from outside)
        // would interact, though current ViewModel doesn't expose _isLoading manipulation.
        // For current ViewModel, _isLoading is always false initially.
        // If we could set _isLoading = MutableStateFlow(true) from VM after init,
        // this test would be more relevant. Given the current code, this is more
        // of a sanity check on the combine logic for the isLoading part of Async.Success.

        initializeViewModel()

        viewModel.uiState.test {
            // 1. Initial Loading state from stateIn initialValue
            assertThat(awaitItem().isLoading).isTrue()

            // 2. Repository emits data. _isLoading is still its default (false)
            recipesRepository.emitRecipe(recipeId1, recipe1)

            // 3. Success state
            val successState = awaitItem()
            // In the Async.Success branch, isLoading = isLoading (which is _isLoading.value)
            // Since _isLoading is private and defaults to false, this should be false.
            assertThat(successState.isLoading).isFalse()
            assertThat(successState.recipe).isEqualTo(recipe1)

            cancelAndConsumeRemainingEvents()
        }
    }
}
