package com.tenmilelabs.chefai.home.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.testutil.TEST_DOMAIN_RECIPE_PREVIEWS_LIST
import com.tenmilelabs.chefai.core.testutil.recipePreview1
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class HomeViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: HomeViewModel
    private lateinit var recipesRepository: FakeRecipesRepository

    @Before
    fun setup() {
        recipesRepository = FakeRecipesRepository()
        viewModel = HomeViewModel(recipesRepository)
    }

    @Test
    fun `initial state is loading when no data emitted yet`() = runTest {
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.isLoading).isTrue()
            assertThat(initialState.recipes).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState shows recipes when repository emits data`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.recipes).isEqualTo(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)
            assertThat(state.recipes).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState shows empty list when repository emits empty data`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(emptyList())

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.recipes).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState shows error and emits ShowSnackbar when repository throws`() = runTest {
        val errorRepository = FakeRecipesRepository()
        errorRepository.setShouldReturnErrorForGetRecipes(true)
        val testViewModel = HomeViewModel(errorRepository)

        testViewModel.uiState.test {
            val errorState = awaitItem()
            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.recipes).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }

        testViewModel.uiEvents.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(HomeUiEvent.ShowSnackbar::class.java)
            assertThat((event as HomeUiEvent.ShowSnackbar).message)
                .isEqualTo(R.string.loading_recipes_error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState updates when repository emits new data`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(emptyList())

        viewModel.uiState.test {
            val firstState = awaitItem()
            assertThat(firstState.recipes).isEmpty()

            recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1))

            val secondState = awaitItem()
            assertThat(secondState.recipes).hasSize(1)
            assertThat(secondState.recipes[0]).isEqualTo(recipePreview1)

            recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)

            val thirdState = awaitItem()
            assertThat(thirdState.recipes).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoading is false after successful load`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.recipes).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
