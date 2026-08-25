package com.tenmilelabs.chefai.mealplans.ui.shoppinglist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanRepository
import com.tenmilelabs.chefai.mealplans.data.repository.FakeShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.PlannedIngredient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class ShoppingListViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var mealPlanRepository: FakeMealPlanRepository
    private lateinit var shoppingListRepository: FakeShoppingListRepository

    private val planId = UUID.randomUUID()
    private val recipeId = UUID.randomUUID()

    @Before
    fun setup() {
        mealPlanRepository = FakeMealPlanRepository()
        shoppingListRepository = FakeShoppingListRepository()
    }

    @Test
    fun `emits NotFound for a plan that does not exist`() = runTest {
        mealPlanRepository.emitPlans()

        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is ShoppingListUiState.Loading) awaitItem() else it }
            assertThat(state).isEqualTo(ShoppingListUiState.NotFound)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a plan with no days resolves to an empty list`() = runTest {
        mealPlanRepository.emitPlans(planWith(days = emptyList()))

        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is ShoppingListUiState.Loading) awaitItem() else it }
            val success = state as ShoppingListUiState.Success
            assertThat(success.list.isEmpty).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ticking an item flips its checked state`() = runTest {
        mealPlanRepository.emitPlans(planWith(days = listOf(fullDay())))
        shoppingListRepository.setIngredientsForRecipe(
            recipeId,
            listOf(PlannedIngredient(recipeId, 2, "Onion", 1.0, "cup")),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
                .let { if (it is ShoppingListUiState.Loading) awaitItem() else it }
                as ShoppingListUiState.Success
            val item = initial.list.sections.single().items.single()
            assertThat(item.isChecked).isFalse()

            viewModel.onToggleItem(item)

            val updated = awaitItem() as ShoppingListUiState.Success
            assertThat(updated.list.sections.single().items.single().isChecked).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onUncheckAll clears every tick`() = runTest {
        mealPlanRepository.emitPlans(planWith(days = listOf(fullDay())))
        shoppingListRepository.setIngredientsForRecipe(
            recipeId,
            listOf(PlannedIngredient(recipeId, 2, "Onion", 1.0, "cup")),
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
                .let { if (it is ShoppingListUiState.Loading) awaitItem() else it }
                as ShoppingListUiState.Success
            viewModel.onToggleItem(initial.list.sections.single().items.single())
            awaitItem()

            viewModel.onUncheckAll()

            val cleared = awaitItem() as ShoppingListUiState.Success
            assertThat(cleared.list.checkedCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a repository failure surfaces an error event`() = runTest {
        mealPlanRepository.emitPlans(planWith(days = listOf(fullDay())))
        shoppingListRepository.setIngredientsForRecipe(
            recipeId,
            listOf(PlannedIngredient(recipeId, 2, "Onion", 1.0, "cup")),
        )
        shoppingListRepository.shouldThrowOnSetChecked = true
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
                .let { if (it is ShoppingListUiState.Loading) awaitItem() else it }
                as ShoppingListUiState.Success
            val item = initial.list.sections.single().items.single()

            viewModel.events.test {
                viewModel.onToggleItem(item)
                assertThat(awaitItem()).isInstanceOf(ShoppingListEvent.ShowError::class.java)
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Helpers ---

    private fun createViewModel() = ShoppingListViewModel(
        savedStateHandle = SavedStateHandle().apply {
            set(AppDestinationArgs.MEAL_PLAN_ID_ARG, planId.toString())
        },
        mealPlanRepository = mealPlanRepository,
        shoppingListRepository = shoppingListRepository,
    )

    private fun fullDay() = MealPlanDay(
        uuid = UUID.randomUUID(),
        dayIndex = 0,
        dinnerRecipeId = recipeId,
        lunchRecipeId = null,
    )

    private fun planWith(days: List<MealPlanDay>) = MealPlan(
        uuid = planId,
        userId = UUID.randomUUID(),
        name = "3-day meal plan",
        preferences = MealPlanPreferences(
            planLengthDays = 3,
            mealType = MealType.DINNER,
            dietaryRestrictions = setOf(DietaryRestriction.NONE),
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
        days = days,
    )
}
