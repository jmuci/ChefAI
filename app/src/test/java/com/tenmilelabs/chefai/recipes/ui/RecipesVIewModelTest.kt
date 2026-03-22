package com.tenmilelabs.chefai.recipes.ui


import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.collections.data.repository.FakeCollectionsRepository
import com.tenmilelabs.chefai.core.data.sync.FakeSyncManager
import com.tenmilelabs.chefai.core.data.sync.SyncStatusHolder
import com.tenmilelabs.chefai.core.testutil.TEST_DOMAIN_RECIPE_PREVIEWS_LIST
import com.tenmilelabs.chefai.core.testutil.createRealSessionManagerWithFakes
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.testutil.recipeId1
import com.tenmilelabs.chefai.core.testutil.recipeId2
import com.tenmilelabs.chefai.core.testutil.recipeId3
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
import java.util.UUID

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
    private lateinit var collectionsRepository: FakeCollectionsRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var fakeSyncManager: FakeSyncManager
    private lateinit var syncStatusHolder: SyncStatusHolder

    @Before
    fun setup() {
        recipesRepository = FakeRecipesRepository()
        collectionsRepository = FakeCollectionsRepository()
        sessionManager = createRealSessionManagerWithFakes()
        fakeSyncManager = FakeSyncManager()
        syncStatusHolder = SyncStatusHolder()

        viewModel = RecipesViewModel(recipesRepository, collectionsRepository, sessionManager, fakeSyncManager, syncStatusHolder)
    }

    /** Helper: seed the recipe "universe" and bookmark the given IDs. */
    private fun seedBookmarkedRecipes(
        allPreviews: List<RecipePreview>,
        bookmarkedIds: Set<UUID>,
        repo: FakeRecipesRepository = recipesRepository,
        collections: FakeCollectionsRepository = collectionsRepository,
    ) {
        repo.setRecipePreviewsToEmit(allPreviews)
        collections.setBookmarkedIds(bookmarkedIds)
    }

    @Test
    fun `initial state is empty when no recipes are bookmarked`() = runTest {
        // Collections repo emits empty set immediately via StateFlow,
        // so the flow resolves to Success(empty) right away.
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.isLoading).isFalse()
            assertThat(initialState.items).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState reflects emitted recipes with success message`() = runTest {
        seedBookmarkedRecipes(
            TEST_DOMAIN_RECIPE_PREVIEWS_LIST,
            setOf(recipeId1, recipeId2, recipeId3)
        )

        viewModel.uiState.test {
            val successState = awaitItem()
            assertThat(successState.isLoading).isFalse()
            assertThat(successState.items).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState reflects empty list when no recipes are bookmarked`() = runTest {
        // Recipes exist but none are bookmarked
        seedBookmarkedRecipes(TEST_DOMAIN_RECIPE_PREVIEWS_LIST, emptySet())

        viewModel.uiState.test {
            val emptyState = awaitItem()
            assertThat(emptyState.isLoading).isFalse()
            assertThat(emptyState.items).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState shows error and emits ShowSnackbar event when repository throws error`() = runTest {
        val errorRepository = FakeRecipesRepository()
        errorRepository.setShouldReturnErrorForGetRecipes(true)
        val errorCollections = FakeCollectionsRepository()
        errorCollections.setBookmarkedIds(setOf(recipeId1))
        val testViewModel = RecipesViewModel(errorRepository, errorCollections, sessionManager, fakeSyncManager, syncStatusHolder)

        testViewModel.uiState.test {
            val errorState = awaitItem()
            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.items).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

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
        val errorRepository = FakeRecipesRepository()
        errorRepository.setShouldReturnErrorForGetRecipes(true)
        val errorCollections = FakeCollectionsRepository()
        errorCollections.setBookmarkedIds(setOf(recipeId1))
        val testViewModel = RecipesViewModel(errorRepository, errorCollections, sessionManager, fakeSyncManager, syncStatusHolder)

        testViewModel.uiState.test {
            val errorState = awaitItem()
            assertThat(errorState.isLoading).isFalse()
            cancelAndIgnoreRemainingEvents()
        }

        testViewModel.uiEvents.test {
            val firstEvent = awaitItem()
            assertThat(firstEvent).isInstanceOf(RecipesUiEvent.ShowSnackbar::class.java)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState updates when bookmarks change after initialization`() = runTest {
        // Start with all previews available but no bookmarks
        recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)
        collectionsRepository.setBookmarkedIds(emptySet())

        viewModel.uiState.test {
            val firstState = awaitItem()
            assertThat(firstState.items).isEmpty()

            // Bookmark one recipe
            collectionsRepository.setBookmarkedIds(setOf(recipeId1))

            val secondState = awaitItem()
            assertThat(secondState.items).hasSize(1)
            assertThat(secondState.items[0]).isEqualTo(recipePreview1)

            // Bookmark all recipes
            collectionsRepository.setBookmarkedIds(setOf(recipeId1, recipeId2, recipeId3))

            val thirdState = awaitItem()
            assertThat(thirdState.items).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState contains correct recipe data`() = runTest {
        seedBookmarkedRecipes(
            listOf(recipePreview1, recipePreview2),
            setOf(recipeId1, recipeId2)
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.items).hasSize(2)

            val firstRecipe = state.items[0]
            assertThat(firstRecipe.uuid).isEqualTo(recipePreview1.uuid)
            assertThat(firstRecipe.title).isEqualTo("Pancakes")
            assertThat(firstRecipe.description).isEqualTo("Fluffy American-style pancakes.")

            val secondRecipe = state.items[1]
            assertThat(secondRecipe.uuid).isEqualTo(recipePreview2.uuid)
            assertThat(secondRecipe.title).isEqualTo("French Toast")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error state maintains empty items list`() = runTest {
        val errorRepository = FakeRecipesRepository()
        errorRepository.setShouldReturnErrorForGetRecipes(true)
        val errorCollections = FakeCollectionsRepository()
        errorCollections.setBookmarkedIds(setOf(recipeId1))
        val testViewModel = RecipesViewModel(errorRepository, errorCollections, sessionManager, fakeSyncManager, syncStatusHolder)

        testViewModel.uiState.test {
            val errorState = awaitItem()
            assertThat(errorState.isLoading).isFalse()
            assertThat(errorState.items).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

        testViewModel.uiEvents.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(RecipesUiEvent.ShowSnackbar::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoading is false after successful data load`() = runTest {
        seedBookmarkedRecipes(
            TEST_DOMAIN_RECIPE_PREVIEWS_LIST,
            setOf(recipeId1, recipeId2, recipeId3)
        )

        viewModel.uiState.test {
            val loadedState = awaitItem()
            assertThat(loadedState.isLoading).isFalse()
            assertThat(loadedState.items).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState handles transition from error to success`() = runTest {
        recipesRepository.setShouldReturnErrorForGetRecipes(true)
        collectionsRepository.setBookmarkedIds(setOf(recipeId1))
        val errorViewModel = RecipesViewModel(recipesRepository, collectionsRepository, sessionManager, fakeSyncManager, syncStatusHolder)

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

        // Success state with new instances
        val successRepository = FakeRecipesRepository()
        val successCollections = FakeCollectionsRepository()
        successRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)
        successCollections.setBookmarkedIds(setOf(recipeId1, recipeId2, recipeId3))
        val successViewModel = RecipesViewModel(successRepository, successCollections, sessionManager, fakeSyncManager, syncStatusHolder)

        successViewModel.uiState.test {
            val successState = awaitItem()
            assertThat(successState.items).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }

        successViewModel.uiEvents.test {
            expectNoEvents()
        }
    }

    @Test
    fun `uiState handles single bookmarked recipe`() = runTest {
        seedBookmarkedRecipes(
            TEST_DOMAIN_RECIPE_PREVIEWS_LIST,
            setOf(recipeId1)
        )

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
    fun `uiState only shows bookmarked recipes not all recipes`() = runTest {
        // All 3 recipes exist, but only 2 are bookmarked
        seedBookmarkedRecipes(
            TEST_DOMAIN_RECIPE_PREVIEWS_LIST,
            setOf(recipeId1, recipeId3)
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.items).hasSize(2)
            assertThat(state.items.map { it.title }).containsExactly("Pancakes", "Omelette")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState updates from empty to loaded when bookmarks are added`() = runTest {
        // Collections repo emits emptySet() immediately, so initial state is Success(empty)
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.items).isEmpty()

            // Add all recipes and bookmark them
            seedBookmarkedRecipes(
                TEST_DOMAIN_RECIPE_PREVIEWS_LIST,
                setOf(recipeId1, recipeId2, recipeId3)
            )

            val loadedState = awaitItem()
            assertThat(loadedState.isLoading).isFalse()
            assertThat(loadedState.items).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }

        viewModel.uiEvents.test {
            expectNoEvents()
        }
    }

    @Test
    fun `uiState handles multiple rapid bookmark updates`() = runTest {
        recipesRepository.setRecipePreviewsToEmit(TEST_DOMAIN_RECIPE_PREVIEWS_LIST)
        collectionsRepository.setBookmarkedIds(emptySet())

        viewModel.uiState.test {
            assertThat(awaitItem().items).isEmpty()

            collectionsRepository.setBookmarkedIds(setOf(recipeId1))
            assertThat(awaitItem().items).hasSize(1)

            collectionsRepository.setBookmarkedIds(setOf(recipeId1, recipeId2))
            assertThat(awaitItem().items).hasSize(2)

            collectionsRepository.setBookmarkedIds(setOf(recipeId1, recipeId2, recipeId3))
            assertThat(awaitItem().items).hasSize(3)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recipe preview contains all required fields`() = runTest {
        seedBookmarkedRecipes(
            listOf(recipePreview1),
            setOf(recipeId1)
        )

        viewModel.uiState.test {
            val state = awaitItem()
            val recipe = state.items[0]

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
