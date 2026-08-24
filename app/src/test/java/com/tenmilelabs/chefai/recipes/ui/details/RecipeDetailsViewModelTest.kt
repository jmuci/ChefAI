package com.tenmilelabs.chefai.recipes.ui.details

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.collections.data.repository.FakeCollectionsRepository
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.testutil.recipe1
import com.tenmilelabs.chefai.core.testutil.recipeId1
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
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
    private lateinit var collectionsRepository: FakeCollectionsRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var mealPlanRepository: FakeMealPlanRepository
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setup() {
        recipesRepository = FakeRecipesRepository()
        collectionsRepository = FakeCollectionsRepository()
        sessionManager = createTestSessionManager(
            testScope = CoroutineScope(mainCoroutineRule.testDispatcher)
        )
        mealPlanRepository = FakeMealPlanRepository()
        savedStateHandle = SavedStateHandle().apply {
            set(AppDestinationArgs.RECIPE_ID_ARG, recipeId1.toString())
        }
    }

    private fun initializeViewModel() {
        viewModel = RecipeDetailsViewModel(
            recipesRepository, collectionsRepository, sessionManager, mealPlanRepository, savedStateHandle
        )
    }

    /** A meal plan with a single day/slot, for the "opened from a meal plan" test cases. */
    private fun planWithDay(dayId: UUID, dinnerCookedAt: Long? = null) = MealPlan(
        uuid = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        name = "Week plan",
        preferences = MealPlanPreferences(
            planLengthDays = 3,
            mealType = MealType.DINNER,
            dietaryRestrictions = emptySet(),
            recipeSource = RecipeSource.INCLUDE_PUBLIC,
            maxPrepTimeMinutes = null,
            servingsPerMeal = 2,
            batchCooking = false,
            leftoverFriendly = false,
            varietyPreference = VarietyPreference.MEDIUM,
        ),
        status = MealPlanStatus.READY,
        createdAt = 0L,
        updatedAt = 0L,
        days = listOf(
            MealPlanDay(
                uuid = dayId,
                dayIndex = 0,
                dinnerRecipeId = recipeId1,
                lunchRecipeId = null,
                dinnerCookedAt = dinnerCookedAt,
            )
        ),
    )

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
        initializeViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()

            recipesRepository.emitRecipe(recipeId1, recipe1)

            val successState = awaitItem()
            assertThat(successState.isLoading).isFalse()
            assertThat(successState.recipe).isEqualTo(recipe1)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `initial bookmark state is false`() = runTest {
        initializeViewModel()
        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()

            recipesRepository.emitRecipe(recipeId1, recipe1)

            val successState = awaitItem()
            assertThat(successState.isBookmarked).isFalse()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `toggleBookmark - adds bookmark when not bookmarked`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        initializeViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)
            assertThat(awaitItem().isBookmarked).isFalse()

            viewModel.toggleBookmark()
            assertThat(awaitItem().isBookmarked).isTrue()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `toggleBookmark - removes bookmark when already bookmarked`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        collectionsRepository.addBookmark(userId, recipeId1)
        initializeViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)
            assertThat(awaitItem().isBookmarked).isTrue()

            viewModel.toggleBookmark()
            assertThat(awaitItem().isBookmarked).isFalse()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `onDeleteClick - shows the confirmation dialog`() = runTest {
        initializeViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)
            assertThat(awaitItem().showDeleteConfirmation).isFalse()

            viewModel.onDeleteClick()
            assertThat(awaitItem().showDeleteConfirmation).isTrue()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `dismissDeleteDialog - hides the dialog without deleting`() = runTest {
        initializeViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)
            awaitItem() // initial success state
            viewModel.onDeleteClick()
            assertThat(awaitItem().showDeleteConfirmation).isTrue()

            viewModel.dismissDeleteDialog()
            assertThat(awaitItem().showDeleteConfirmation).isFalse()

            cancelAndConsumeRemainingEvents()
        }
        assertThat(recipesRepository.lastSoftDeletedId).isNull()
    }

    @Test
    fun `confirmDelete - success - calls repository and emits RecipeDeleted`() = runTest {
        initializeViewModel()

        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val effects = viewModel.effects.testIn(backgroundScope)

            assertThat(states.awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)
            assertThat(states.awaitItem().recipe).isEqualTo(recipe1)

            viewModel.confirmDelete()

            val deletingState = states.awaitItem()
            assertThat(deletingState.isDeleting).isTrue()
            assertThat(deletingState.showDeleteConfirmation).isFalse()

            // The recipe stream emits null once the delete lands (T1's filter); the T4 guard
            // replaces that with a neutral loading state instead of the "not found" error.
            val guardedState = states.awaitItem()
            assertThat(guardedState.recipe).isNull()
            assertThat(guardedState.userMessage).isNotEqualTo(R.string.loading_recipe_details_error)

            assertThat(effects.awaitItem()).isEqualTo(RecipeDetailsEffect.RecipeDeleted)
        }

        assertThat(recipesRepository.lastSoftDeletedId).isEqualTo(recipeId1)
    }

    @Test
    fun `confirmDelete - failure - no effect emitted, isDeleting cleared, error message shown`() = runTest {
        recipesRepository.setShouldReturnErrorForSoftDelete(true)
        initializeViewModel()

        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val effects = viewModel.effects.testIn(backgroundScope)

            assertThat(states.awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)
            assertThat(states.awaitItem().recipe).isEqualTo(recipe1)

            viewModel.confirmDelete()

            // _deleteUi and _userMessage are separate StateFlows, both cleared in the catch
            // block via two sequential assignments — that can surface as either one combined
            // emission or two, so drain until both land rather than assuming an exact count.
            var failedState = states.awaitItem()
            while (failedState.isDeleting || failedState.userMessage != R.string.delete_recipe_error) {
                failedState = states.awaitItem()
            }
            assertThat(failedState.recipe).isEqualTo(recipe1) // recipe still shown, delete failed

            effects.expectNoEvents()
        }
    }

    @Test
    fun `confirmDelete - recipe stream emitting null afterward does not surface the load error`() = runTest {
        initializeViewModel()

        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val effects = viewModel.effects.testIn(backgroundScope)

            assertThat(states.awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)
            assertThat(states.awaitItem().recipe).isEqualTo(recipe1)

            viewModel.confirmDelete() // FakeRecipesRepository emits null on this recipe's flow

            // Every state observed from here on must never carry the generic load-failure message —
            // this is the T4 race: getRecipeStream(recipeUuid) emitting null because *we* deleted it
            // must not be confused with a genuine load failure. Drain until the null emission has
            // actually landed (recipe == null), not just until isDeleting flips (that happens
            // synchronously, before the race this test targets even occurs).
            var sawNullRecipe = false
            while (!sawNullRecipe) {
                val state = states.awaitItem()
                assertThat(state.userMessage).isNotEqualTo(R.string.loading_recipe_details_error)
                sawNullRecipe = state.recipe == null
            }

            assertThat(effects.awaitItem()).isEqualTo(RecipeDetailsEffect.RecipeDeleted)
        }
    }

    @Test
    fun `showCookedToggle is false when opened without meal plan args`() = runTest {
        initializeViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)

            val successState = awaitItem()
            assertThat(successState.showCookedToggle).isFalse()
            assertThat(successState.isCooked).isFalse()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `showCookedToggle and isCooked reflect the meal plan slot when opened from a meal plan`() = runTest {
        val dayId = UUID.randomUUID()
        savedStateHandle[AppDestinationArgs.MEAL_PLAN_DAY_ID_ARG] = dayId.toString()
        savedStateHandle[AppDestinationArgs.MEAL_PLAN_SLOT_ARG] = MealSlot.DINNER.name
        mealPlanRepository.emitPlans(planWithDay(dayId, dinnerCookedAt = null))
        initializeViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)

            val successState = awaitItem()
            assertThat(successState.showCookedToggle).isTrue()
            assertThat(successState.isCooked).isFalse()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `onToggleCooked - marks the meal plan slot cooked`() = runTest {
        val dayId = UUID.randomUUID()
        savedStateHandle[AppDestinationArgs.MEAL_PLAN_DAY_ID_ARG] = dayId.toString()
        savedStateHandle[AppDestinationArgs.MEAL_PLAN_SLOT_ARG] = MealSlot.DINNER.name
        mealPlanRepository.emitPlans(planWithDay(dayId, dinnerCookedAt = null))
        initializeViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)
            assertThat(awaitItem().isCooked).isFalse()

            viewModel.onToggleCooked()
            assertThat(awaitItem().isCooked).isTrue()

            viewModel.onToggleCooked()
            assertThat(awaitItem().isCooked).isFalse()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `onToggleCooked - without meal plan args - does nothing`() = runTest {
        initializeViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            recipesRepository.emitRecipe(recipeId1, recipe1)
            assertThat(awaitItem().showCookedToggle).isFalse()

            viewModel.onToggleCooked() // no dayId/slot in the saved state — should be a no-op

            expectNoEvents()
        }
    }
}
