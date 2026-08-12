package com.tenmilelabs.chefai.mealplans.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class MealPlansViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var repository: FakeMealPlanRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: MealPlansViewModel

    @Before
    fun setup() {
        repository = FakeMealPlanRepository()
        sessionManager = createTestSessionManager(CoroutineScope(mainCoroutineRule.testDispatcher))
        viewModel = MealPlansViewModel(
            mealPlanRepository = repository,
            sessionManager = sessionManager,
        )
    }

    private fun makePlan(
        userId: UUID,
        name: String = "Test Plan",
        status: MealPlanStatus = MealPlanStatus.DRAFT,
    ) = MealPlan(
        uuid = UUID.randomUUID(),
        userId = userId,
        name = name,
        preferences = MealPlanPreferences(
            planLengthDays = 5,
            mealType = MealType.DINNER,
            dietaryRestrictions = emptySet<DietaryRestriction>(),
            recipeSource = RecipeSource.COLLECTION_ONLY,
            maxPrepTimeMinutes = null,
            servingsPerMeal = 2,
            batchCooking = false,
            leftoverFriendly = false,
            varietyPreference = VarietyPreference.MEDIUM,
        ),
        status = status,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        days = emptyList(),
    )

    // --- Initial state ---

    @Test
    fun `initial uiState is Loading or immediately Success when session is pre-loaded`() = runTest {
        // With UnconfinedTestDispatcher + a pre-loaded session the upstream emits synchronously,
        // so the first observable state is Success(empty) rather than Loading.
        viewModel.uiState.test {
            val first = awaitItem()
            assertThat(first is MealPlansUiState.Loading || first is MealPlansUiState.Success).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Success states ---

    @Test
    fun `uiState is Success with empty list when no plans exist for user`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        repository.emitPlans() // empty

        viewModel.uiState.test {
            val state = awaitItem().let { if (it is MealPlansUiState.Loading) awaitItem() else it }
            assertThat(state).isInstanceOf(MealPlansUiState.Success::class.java)
            assertThat((state as MealPlansUiState.Success).mealPlans).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState is Success with plans for the current user`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val plan1 = makePlan(userId, "Plan A")
        val plan2 = makePlan(userId, "Plan B")
        repository.emitPlans(plan1, plan2)

        viewModel.uiState.test {
            val state = awaitItem().let { if (it is MealPlansUiState.Loading) awaitItem() else it }
            assertThat(state).isInstanceOf(MealPlansUiState.Success::class.java)
            val plans = (state as MealPlansUiState.Success).mealPlans
            assertThat(plans).hasSize(2)
            assertThat(plans.map { it.name }).containsExactly("Plan A", "Plan B")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState excludes plans belonging to a different user`() = runTest {
        val currentUserId = sessionManager.getCurrentUserId()!!
        val otherUserId = UUID.randomUUID()
        repository.emitPlans(
            makePlan(currentUserId, "My Plan"),
            makePlan(otherUserId, "Other Plan"),
        )

        viewModel.uiState.test {
            val state = awaitItem().let { if (it is MealPlansUiState.Loading) awaitItem() else it }
            val plans = (state as MealPlansUiState.Success).mealPlans
            assertThat(plans).hasSize(1)
            assertThat(plans.first().name).isEqualTo("My Plan")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState updates reactively when plans change`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val plan1 = makePlan(userId, "Plan A")
        repository.emitPlans(plan1)

        viewModel.uiState.test {
            // Consume initial emission (1 plan)
            val first = awaitItem().let { if (it is MealPlansUiState.Loading) awaitItem() else it }
            assertThat((first as MealPlansUiState.Success).mealPlans).hasSize(1)

            // Add a second plan
            val plan2 = makePlan(userId, "Plan B")
            repository.emitPlans(plan1, plan2)
            val updated = awaitItem()
            assertThat((updated as MealPlansUiState.Success).mealPlans).hasSize(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Error state ---

    @Test
    fun `uiState is Error when repository throws`() = runTest {
        val errorRepository = FakeMealPlanRepository().also { it.shouldThrowOnObserve = true }
        val errorViewModel = MealPlansViewModel(
            mealPlanRepository = errorRepository,
            sessionManager = sessionManager,
        )

        errorViewModel.uiState.test {
            val state = awaitItem().let { if (it is MealPlansUiState.Loading) awaitItem() else it }
            assertThat(state).isInstanceOf(MealPlansUiState.Error::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Delete ---

    @Test
    fun `onDeleteMealPlan removes plan from repository`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val plan = makePlan(userId, "To Delete")
        repository.emitPlans(plan)

        viewModel.uiState.test {
            awaitItem().let { if (it is MealPlansUiState.Loading) awaitItem() else it }

            viewModel.onDeleteMealPlan(plan.uuid)

            val updated = awaitItem()
            assertThat((updated as MealPlansUiState.Success).mealPlans).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDeleteMealPlan emits ShowSnackbar with deleted message on success`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val plan = makePlan(userId)
        repository.emitPlans(plan)

        viewModel.uiEvents.test {
            viewModel.onDeleteMealPlan(plan.uuid)
            val event = awaitItem()
            assertThat(event).isInstanceOf(MealPlansEvent.ShowSnackbar::class.java)
            assertThat((event as MealPlansEvent.ShowSnackbar).message)
                .isEqualTo(R.string.meal_plan_deleted)
        }
    }

    @Test
    fun `onDeleteMealPlan emits ShowSnackbar with error message when repository throws`() = runTest {
        repository.shouldThrowOnDelete = true
        val userId = sessionManager.getCurrentUserId()!!
        val plan = makePlan(userId)
        repository.emitPlans(plan)

        viewModel.uiEvents.test {
            viewModel.onDeleteMealPlan(plan.uuid)
            val event = awaitItem()
            assertThat(event).isInstanceOf(MealPlansEvent.ShowSnackbar::class.java)
            assertThat((event as MealPlansEvent.ShowSnackbar).message)
                .isEqualTo(R.string.meal_plan_error)
        }
    }
}
