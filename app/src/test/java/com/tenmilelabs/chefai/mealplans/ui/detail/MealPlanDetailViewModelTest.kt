package com.tenmilelabs.chefai.mealplans.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.sync.FakeSyncExecutor
import com.tenmilelabs.chefai.core.testutil.recipePreview1
import com.tenmilelabs.chefai.core.testutil.recipePreview2
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.mealplans.domain.usecase.LocalMealPlanGenerator
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@ExperimentalCoroutinesApi
class MealPlanDetailViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var mealPlanRepository: FakeMealPlanRepository
    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var syncExecutor: FakeSyncExecutor

    private val planId = UUID.randomUUID()
    private val dayId = UUID.randomUUID()

    @Before
    fun setup() {
        mealPlanRepository = FakeMealPlanRepository()
        recipesRepository = FakeRecipesRepository()
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1, recipePreview2))
        syncExecutor = FakeSyncExecutor()
    }

    // --- State ---

    @Test
    fun `resolves a plan's meals into day sections`() = runTest {
        mealPlanRepository.emitPlans(planWith(fullDay()))

        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is MealPlanDetailUiState.Loading) awaitItem() else it }
            assertThat(state).isInstanceOf(MealPlanDetailUiState.Success::class.java)
            val success = state as MealPlanDetailUiState.Success
            assertThat(success.upcoming).hasSize(1)
            assertThat(success.upcoming.single().meals).hasSize(2)
            assertThat(success.cooked).isEmpty()
            assertThat(success.totalCount).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits NotFound for a plan that does not exist`() = runTest {
        mealPlanRepository.emitPlans()

        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is MealPlanDetailUiState.Loading) awaitItem() else it }
            assertThat(state).isEqualTo(MealPlanDetailUiState.NotFound)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a lunch-and-dinner plan shows slot labels, a dinner-only plan does not`() = runTest {
        mealPlanRepository.emitPlans(planWith(fullDay(), mealType = MealType.DINNER_AND_LUNCH))
        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is MealPlanDetailUiState.Loading) awaitItem() else it }
            assertThat((state as MealPlanDetailUiState.Success).showsSlotLabels).isTrue()
            cancelAndIgnoreRemainingEvents()
        }

        mealPlanRepository.emitPlans(
            planWith(
                MealPlanDay(dayId, 0, dinnerRecipeId = recipePreview1.uuid, lunchRecipeId = null),
                mealType = MealType.DINNER,
            )
        )
        createViewModel().uiState.test {
            val state = awaitItem().let { if (it is MealPlanDetailUiState.Loading) awaitItem() else it }
            assertThat((state as MealPlanDetailUiState.Success).showsSlotLabels).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Marking cooked ---

    @Test
    fun `toggling a meal cooked moves it to the cooked list`() = runTest {
        mealPlanRepository.emitPlans(planWith(fullDay()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
                .let { if (it is MealPlanDetailUiState.Loading) awaitItem() else it }
                as MealPlanDetailUiState.Success
            val meal = initial.upcoming.single().meals.first { it.slot == MealSlot.LUNCH }

            viewModel.onToggleCooked(meal)

            val updated = awaitItem() as MealPlanDetailUiState.Success
            assertThat(updated.cooked.map { it.slot }).containsExactly(MealSlot.LUNCH)
            assertThat(updated.upcoming.single().meals.map { it.slot })
                .containsExactly(MealSlot.DINNER)
            assertThat(updated.cookedCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling a cooked meal again returns it to upcoming`() = runTest {
        mealPlanRepository.emitPlans(planWith(fullDay(lunchCookedAt = 50L)))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val initial = awaitItem()
                .let { if (it is MealPlanDetailUiState.Loading) awaitItem() else it }
                as MealPlanDetailUiState.Success

            viewModel.onToggleCooked(initial.cooked.single())

            val updated = awaitItem() as MealPlanDetailUiState.Success
            assertThat(updated.cooked).isEmpty()
            assertThat(updated.upcoming.single().meals).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cooking every meal completes the plan's progress`() = runTest {
        mealPlanRepository.emitPlans(planWith(fullDay(lunchCookedAt = 1L, dinnerCookedAt = 2L)))

        createViewModel().uiState.test {
            val state = awaitItem()
                .let { if (it is MealPlanDetailUiState.Loading) awaitItem() else it }
                as MealPlanDetailUiState.Success
            assertThat(state.progress).isEqualTo(1f)
            assertThat(state.upcoming).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Generation ---

    @Test
    fun `onGenerate uses the server schedule when it returns one`() = runTest {
        mealPlanRepository.emitPlans(emptyPlan())
        mealPlanRepository.daysFromServer = listOf(fullDay())

        createViewModel().onGenerate()
        advanceUntilIdle()

        assertThat(mealPlanRepository.generationRequestedIds).containsExactly(planId)
        assertThat(mealPlanRepository.locallyGeneratedIds).isEmpty()
        assertThat(mealPlanRepository.observeMealPlan(planId).first()!!.days).hasSize(1)
    }

    @Test
    fun `onGenerate falls back to on-device generation when the server call fails`() = runTest {
        mealPlanRepository.emitPlans(emptyPlan())
        mealPlanRepository.shouldFailGeneration = true

        createViewModel().onGenerate()
        advanceUntilIdle()

        assertThat(mealPlanRepository.locallyGeneratedIds).containsExactly(planId)
        assertThat(mealPlanRepository.observeMealPlan(planId).first()!!.days).hasSize(3)
    }

    @Test
    fun `onGenerate falls back when the server accepts but delivers no days`() = runTest {
        // The anonymous case: the request succeeds but pulled meal plans are skipped.
        mealPlanRepository.emitPlans(emptyPlan())

        createViewModel().onGenerate()
        advanceUntilIdle()

        assertThat(mealPlanRepository.generationRequestedIds).containsExactly(planId)
        assertThat(mealPlanRepository.locallyGeneratedIds).containsExactly(planId)
    }

    @Test
    fun `onGenerate reports an error when there is nothing to build a plan from`() = runTest {
        mealPlanRepository.emitPlans(emptyPlan())
        mealPlanRepository.shouldFailGeneration = true
        recipesRepository.setRecipePreviewsToEmit(emptyList())
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onGenerate()
            assertThat(awaitItem()).isInstanceOf(MealPlanDetailEvent.ShowError::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Helpers ---

    private fun createViewModel() = MealPlanDetailViewModel(
        savedStateHandle = SavedStateHandle().apply {
            set(AppDestinationArgs.MEAL_PLAN_ID_ARG, planId.toString())
        },
        mealPlanRepository = mealPlanRepository,
        recipesRepository = recipesRepository,
        syncExecutor = syncExecutor,
        localMealPlanGenerator = LocalMealPlanGenerator(recipesRepository, mealPlanRepository),
    )

    private fun fullDay(lunchCookedAt: Long? = null, dinnerCookedAt: Long? = null) = MealPlanDay(
        uuid = dayId,
        dayIndex = 0,
        dinnerRecipeId = recipePreview2.uuid,
        lunchRecipeId = recipePreview1.uuid,
        dinnerCookedAt = dinnerCookedAt,
        lunchCookedAt = lunchCookedAt,
    )

    private fun emptyPlan() = planWith(days = emptyList())

    private fun planWith(
        day: MealPlanDay,
        mealType: MealType = MealType.DINNER_AND_LUNCH,
    ) = planWith(listOf(day), mealType)

    private fun planWith(
        days: List<MealPlanDay>,
        mealType: MealType = MealType.DINNER_AND_LUNCH,
    ) = MealPlan(
        uuid = planId,
        userId = UUID.randomUUID(),
        name = "3-day meal plan",
        preferences = MealPlanPreferences(
            planLengthDays = 3,
            mealType = mealType,
            dietaryRestrictions = setOf(DietaryRestriction.NONE),
            recipeSource = RecipeSource.INCLUDE_PUBLIC,
            maxPrepTimeMinutes = null,
            servingsPerMeal = 2,
            batchCooking = false,
            leftoverFriendly = false,
            varietyPreference = VarietyPreference.MEDIUM,
        ),
        status = MealPlanStatus.DRAFT,
        createdAt = 0L,
        updatedAt = 0L,
        days = days,
    )
}
