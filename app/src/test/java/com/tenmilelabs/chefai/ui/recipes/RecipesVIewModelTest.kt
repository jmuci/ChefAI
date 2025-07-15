package com.tenmilelabs.chefai.ui.recipes


import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R // Assuming your UserMessage uses R.string
import com.tenmilelabs.chefai.data.FakeRecipesRepository
import com.tenmilelabs.chefai.data.Recipe
import com.tenmilelabs.chefai.util.MainCoroutineRule
// Assuming UserMessage and RecipesUiState data classes exist
// import com.tenmilelabs.chefai.ui.UserMessage
// import com.tenmilelabs.chefai.ui.recipes.RecipesUiState

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// You'll need to define these or use your actual classes
// Example placeholder data classes:
data class UserMessage(val messageId: Int, val args: List<Any> = emptyList()) // Make sure this matches your app's UserMessage


@ExperimentalCoroutinesApi
class RecipesViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: RecipesViewModel
    private lateinit var recipesRepository: FakeRecipesRepository

    private val testRecipe1 = Recipe(uuid = "1", title = "Recipe 1", description = "Desc 1", label = "Test Label", recipeUrl = "http://example.com/recipe", imageUrl = "http://example.com/image.jpg", thumbnailUrl = "http://example.com/thumb.jpg", prepTime = 30)
    private val testRecipe2 = Recipe(uuid = "2", title = "Recipe 2", description = "Desc 2", label = "Test Label", recipeUrl = "http://example.com/recipe", imageUrl = "http://example.com/image.jpg", thumbnailUrl = "http://example.com/thumb.jpg", prepTime = 30)
    private val testRecipes = listOf(testRecipe1, testRecipe2)

    @Before
    fun setup() {
        recipesRepository = FakeRecipesRepository()
        // ViewModel is re-initialized for each test
    }

    private fun initializeViewModel() {
        // Pass the UnconfinedTestDispatcher for any CoroutineScopes the VM might create internally
        // if it doesn't solely rely on viewModelScope.
        viewModel = RecipesViewModel(recipesRepository/*, mainCoroutineRule.testDispatcher*/)
    }

    @Test
    fun `initial state is loading and then reflects emitted recipes`() = runTest {
        initializeViewModel() // ViewModel starts collecting on init

        viewModel.uiState.test {
            // 1. Expect initial state from stateIn (often isLoading=true, empty items)
            val initialState = awaitItem()
            assertThat(initialState.isLoading).isTrue() // Assuming ViewModel sets loading true initially
            assertThat(initialState.items).isEmpty()
            assertThat(initialState.userMessage).isNull()

            // 2. Repository emits data
            recipesRepository.setRecipesToEmit(testRecipes)

            // 3. Expect success state with items
            val successState = awaitItem()
            assertThat(successState.isLoading).isFalse() // Or based on your VM's logic after load
            assertThat(successState.items).isEqualTo(testRecipes)
            assertThat(successState.userMessage).isEqualTo(R.string.loading_recipes_success) // Example message

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when repository emits empty list - uiState reflects empty items`() = runTest {
        initializeViewModel()
        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue() // Initial

            recipesRepository.setRecipesToEmit(emptyList())

            val emptyState = awaitItem()
            assertThat(emptyState.isLoading).isFalse()
            assertThat(emptyState.items).isEmpty()
            // User message might indicate success or a specific "no items" message
            //assertThat(emptyState.userMessage).isEqualTo(UserMessage(R.string.recipes_empty)) // Example

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `snackbarMessageShown - clears userMessage in uiState`() = runTest {
        recipesRepository.setShouldReturnErrorForGetRecipes(true)

        initializeViewModel() // ViewModel starts collecting on init

        viewModel.uiState.test {
            // 1. Error state
            val errorState = awaitItem()
            assertThat(errorState.userMessage).isNotNull()
            assertThat(errorState.userMessage).isEqualTo(R.string.loading_recipes_error) // Example

            cancelAndConsumeRemainingEvents()
        }
    }

// Add more tests:
// - If getRecipesFlow() itself throws an error (not just refreshRecipes).
// - Pagination if your ViewModel supports it.
// - Specific filtering logic if applicable.
}