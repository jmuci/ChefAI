package com.tenmilelabs.chefai.mealplans.ui.create

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.sync.FakeSyncExecutor
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanRepository
import com.tenmilelabs.chefai.core.testutil.recipePreview1
import com.tenmilelabs.chefai.core.testutil.recipePreview2
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.mealplans.domain.usecase.LocalMealPlanGenerator
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class CreateMealPlanViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var repository: FakeMealPlanRepository
    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var syncExecutor: FakeSyncExecutor
    private lateinit var viewModel: CreateMealPlanViewModel

    @Before
    fun setup() {
        repository = FakeMealPlanRepository()
        recipesRepository = FakeRecipesRepository()
        recipesRepository.fakeRecipeCount = 50 // enough to allow COLLECTION_ONLY
        // No local recipes by default, so the on-device fallback cannot fill a plan and the
        // remote path alone decides the outcome. Tests that exercise the fallback seed this.
        recipesRepository.setRecipePreviewsToEmit(emptyList())
        sessionManager = createTestSessionManager(CoroutineScope(mainCoroutineRule.testDispatcher))
        syncExecutor = FakeSyncExecutor()
        viewModel = createViewModel()
    }

    private fun createViewModel(
        recipeCount: Int = recipesRepository.fakeRecipeCount,
    ): CreateMealPlanViewModel {
        recipesRepository.fakeRecipeCount = recipeCount
        return CreateMealPlanViewModel(
            mealPlanRepository = repository,
            sessionManager = sessionManager,
            syncExecutor = syncExecutor,
            recipesRepository = recipesRepository,
            localMealPlanGenerator = LocalMealPlanGenerator(recipesRepository, repository),
        )
    }

    // --- Default state ---

    @Test
    fun `initial state has correct defaults`() = runTest {
        val state = viewModel.uiState.value
        assertThat(state.planLengthDays).isEqualTo(5)
        assertThat(state.mealType).isEqualTo(MealType.DINNER)
        assertThat(state.servingsPerMeal).isEqualTo(2)
        assertThat(state.dietaryRestrictions).isEmpty()
        assertThat(state.recipeSource).isEqualTo(RecipeSource.COLLECTION_ONLY)
        assertThat(state.maxPrepTimeMinutes).isNull()
        assertThat(state.batchCooking).isFalse()
        assertThat(state.leftoverFriendly).isFalse()
        assertThat(state.varietyPreference).isEqualTo(VarietyPreference.MEDIUM)
        assertThat(state.isSaving).isFalse()
    }

    // --- Step 1: Basics ---

    @Test
    fun `SetPlanLength updates planLengthDays`() = runTest {
        viewModel.onAction(WizardAction.SetPlanLength(7))
        assertThat(viewModel.uiState.value.planLengthDays).isEqualTo(7)
    }

    @Test
    fun `SetMealType updates mealType`() = runTest {
        viewModel.onAction(WizardAction.SetMealType(MealType.DINNER_AND_LUNCH))
        assertThat(viewModel.uiState.value.mealType).isEqualTo(MealType.DINNER_AND_LUNCH)
    }

    @Test
    fun `SetServings updates servingsPerMeal`() = runTest {
        viewModel.onAction(WizardAction.SetServings(4))
        assertThat(viewModel.uiState.value.servingsPerMeal).isEqualTo(4)
    }

    @Test
    fun `SetServings clamps value to minimum of 1`() = runTest {
        viewModel.onAction(WizardAction.SetServings(0))
        assertThat(viewModel.uiState.value.servingsPerMeal).isEqualTo(1)
    }

    @Test
    fun `SetServings clamps value to maximum of 12`() = runTest {
        viewModel.onAction(WizardAction.SetServings(99))
        assertThat(viewModel.uiState.value.servingsPerMeal).isEqualTo(12)
    }

    // --- Step 2: Preferences ---

    @Test
    fun `ToggleDietaryRestriction adds restriction when not selected`() = runTest {
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.VEGAN))
        assertThat(viewModel.uiState.value.dietaryRestrictions).contains(DietaryRestriction.VEGAN)
    }

    @Test
    fun `ToggleDietaryRestriction removes restriction when already selected`() = runTest {
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.VEGAN))
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.VEGAN))
        assertThat(viewModel.uiState.value.dietaryRestrictions).doesNotContain(DietaryRestriction.VEGAN)
    }

    @Test
    fun `ToggleDietaryRestriction selecting NONE clears others and sets NONE`() = runTest {
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.VEGAN))
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.GLUTEN_FREE))
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.NONE))
        assertThat(viewModel.uiState.value.dietaryRestrictions).containsExactly(DietaryRestriction.NONE)
    }

    @Test
    fun `ToggleDietaryRestriction toggling NONE twice clears everything`() = runTest {
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.NONE))
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.NONE))
        assertThat(viewModel.uiState.value.dietaryRestrictions).isEmpty()
    }

    @Test
    fun `ToggleDietaryRestriction selecting specific restriction removes NONE`() = runTest {
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.NONE))
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.VEGAN))
        assertThat(viewModel.uiState.value.dietaryRestrictions).containsExactly(DietaryRestriction.VEGAN)
    }

    @Test
    fun `SetRecipeSource updates recipeSource`() = runTest {
        viewModel.onAction(WizardAction.SetRecipeSource(RecipeSource.INCLUDE_PUBLIC))
        assertThat(viewModel.uiState.value.recipeSource).isEqualTo(RecipeSource.INCLUDE_PUBLIC)
    }

    @Test
    fun `SetMaxPrepTime updates maxPrepTimeMinutes`() = runTest {
        viewModel.onAction(WizardAction.SetMaxPrepTime(30))
        assertThat(viewModel.uiState.value.maxPrepTimeMinutes).isEqualTo(30)
    }

    @Test
    fun `SetMaxPrepTime with null clears the limit`() = runTest {
        viewModel.onAction(WizardAction.SetMaxPrepTime(30))
        viewModel.onAction(WizardAction.SetMaxPrepTime(null))
        assertThat(viewModel.uiState.value.maxPrepTimeMinutes).isNull()
    }

    // --- Step 3: Advanced ---

    @Test
    fun `SetBatchCooking updates batchCooking`() = runTest {
        viewModel.onAction(WizardAction.SetBatchCooking(true))
        assertThat(viewModel.uiState.value.batchCooking).isTrue()
    }

    @Test
    fun `SetLeftoverFriendly updates leftoverFriendly`() = runTest {
        viewModel.onAction(WizardAction.SetLeftoverFriendly(true))
        assertThat(viewModel.uiState.value.leftoverFriendly).isTrue()
    }

    @Test
    fun `SetVarietyPreference updates varietyPreference`() = runTest {
        viewModel.onAction(WizardAction.SetVarietyPreference(VarietyPreference.HIGH))
        assertThat(viewModel.uiState.value.varietyPreference).isEqualTo(VarietyPreference.HIGH)
    }

    // --- Save ---

    @Test
    fun `SaveMealPlan emits MealPlanReady when the server returns a schedule`() = runTest {
        repository.daysFromServer = listOf(serverDay())

        viewModel.uiEvents.test {
            viewModel.onAction(WizardAction.SaveMealPlan)
            val event = awaitItem()
            assertThat(event).isInstanceOf(CreateMealPlanEvent.MealPlanReady::class.java)
        }
    }

    @Test
    fun `SaveMealPlan emits MealPlanSavedAsDraft when the server accepts but returns no days`() = runTest {
        // The anonymous case: the call succeeds, but pulled meal plans are skipped, so the plan
        // gains nothing. With no local recipes either, there is nothing left to fall back to.
        viewModel.uiEvents.test {
            viewModel.onAction(WizardAction.SaveMealPlan)
            val event = awaitItem()
            assertThat(event).isInstanceOf(CreateMealPlanEvent.MealPlanSavedAsDraft::class.java)
        }
    }

    @Test
    fun `SaveMealPlan emits MealPlanSavedAsDraft when sync fails and there are no local recipes`() = runTest {
        syncExecutor.shouldThrow = true
        viewModel.uiEvents.test {
            viewModel.onAction(WizardAction.SaveMealPlan)
            val event = awaitItem()
            assertThat(event).isInstanceOf(CreateMealPlanEvent.MealPlanSavedAsDraft::class.java)
        }
    }

    @Test
    fun `SaveMealPlan emits MealPlanSavedAsDraft when generation fails and there are no local recipes`() = runTest {
        repository.shouldFailGeneration = true
        viewModel.uiEvents.test {
            viewModel.onAction(WizardAction.SaveMealPlan)
            val event = awaitItem()
            assertThat(event).isInstanceOf(CreateMealPlanEvent.MealPlanSavedAsDraft::class.java)
        }
    }

    @Test
    fun `SaveMealPlan falls back to on-device generation when sync fails`() = runTest {
        syncExecutor.shouldThrow = true
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1, recipePreview2))

        viewModel.uiEvents.test {
            viewModel.onAction(WizardAction.SaveMealPlan)
            val event = awaitItem()
            assertThat(event).isInstanceOf(CreateMealPlanEvent.MealPlanReady::class.java)
        }

        val userId = sessionManager.getCurrentUserId()!!
        val plan = repository.observeMealPlansForUser(userId).first().single()
        assertThat(repository.locallyGeneratedIds).containsExactly(plan.uuid)
        assertThat(plan.days).hasSize(plan.preferences.planLengthDays)
        assertThat(plan.status).isEqualTo(MealPlanStatus.READY)
    }

    @Test
    fun `SaveMealPlan prefers the server schedule over on-device generation`() = runTest {
        repository.daysFromServer = listOf(serverDay())
        recipesRepository.setRecipePreviewsToEmit(listOf(recipePreview1, recipePreview2))

        viewModel.uiEvents.test {
            viewModel.onAction(WizardAction.SaveMealPlan)
            awaitItem()
        }

        assertThat(repository.locallyGeneratedIds).isEmpty()
    }

    @Test
    fun `SaveMealPlan persists plan with correct preferences`() = runTest {
        viewModel.onAction(WizardAction.SetPlanLength(3))
        viewModel.onAction(WizardAction.SetMealType(MealType.DINNER_AND_LUNCH))
        viewModel.onAction(WizardAction.SetServings(4))
        viewModel.onAction(WizardAction.ToggleDietaryRestriction(DietaryRestriction.VEGAN))

        viewModel.uiEvents.test {
            viewModel.onAction(WizardAction.SaveMealPlan)
            awaitItem()
        }

        val userId = sessionManager.getCurrentUserId()!!
        val saved = repository.observeMealPlansForUser(userId).first()
        assertThat(saved).hasSize(1)
        val plan = saved.first()
        assertThat(plan.preferences.planLengthDays).isEqualTo(3)
        assertThat(plan.preferences.mealType).isEqualTo(MealType.DINNER_AND_LUNCH)
        assertThat(plan.preferences.servingsPerMeal).isEqualTo(4)
        assertThat(plan.preferences.dietaryRestrictions).containsExactly(DietaryRestriction.VEGAN)
        assertThat(plan.name).contains("3")
    }

    @Test
    fun `SaveMealPlan resets isSaving to false after success`() = runTest {
        repository.daysFromServer = listOf(serverDay())
        viewModel.uiEvents.test {
            viewModel.onAction(WizardAction.SaveMealPlan)
            awaitItem()
        }
        assertThat(viewModel.uiState.value.isSaving).isFalse()
    }

    @Test
    fun `SaveMealPlan emits ShowError when repository throws`() = runTest {
        repository.shouldThrowOnCreate = true

        viewModel.uiEvents.test {
            viewModel.onAction(WizardAction.SaveMealPlan)
            val event = awaitItem()
            assertThat(event).isInstanceOf(CreateMealPlanEvent.ShowError::class.java)
            assertThat((event as CreateMealPlanEvent.ShowError).message)
                .isEqualTo(R.string.meal_plan_save_error)
        }
    }

    @Test
    fun `SaveMealPlan resets isSaving to false after error`() = runTest {
        repository.shouldThrowOnCreate = true

        viewModel.uiEvents.test {
            viewModel.onAction(WizardAction.SaveMealPlan)
            awaitItem()
        }
        assertThat(viewModel.uiState.value.isSaving).isFalse()
    }

    // --- Collection too small ---

    @Test
    fun `collectionTooSmall is true and recipeSource defaults to INCLUDE_PUBLIC when under 20 recipes`() = runTest {
        val vm = createViewModel(recipeCount = 5)
        val state = vm.uiState.value
        assertThat(state.collectionTooSmall).isTrue()
        assertThat(state.recipeSource).isEqualTo(RecipeSource.INCLUDE_PUBLIC)
    }

    @Test
    fun `collectionTooSmall is false when user has 20 or more recipes`() = runTest {
        val vm = createViewModel(recipeCount = 20)
        val state = vm.uiState.value
        assertThat(state.collectionTooSmall).isFalse()
        assertThat(state.recipeSource).isEqualTo(RecipeSource.COLLECTION_ONLY)
    }

    @Test
    fun `SetRecipeSource to COLLECTION_ONLY is ignored when collection is too small`() = runTest {
        val vm = createViewModel(recipeCount = 5)
        vm.onAction(WizardAction.SetRecipeSource(RecipeSource.COLLECTION_ONLY))
        assertThat(vm.uiState.value.recipeSource).isEqualTo(RecipeSource.INCLUDE_PUBLIC)
    }

    /** A day standing in for one the backend generator produced. */
    private fun serverDay() = MealPlanDay(
        uuid = UUID.randomUUID(),
        dayIndex = 0,
        dinnerRecipeId = UUID.randomUUID(),
        lunchRecipeId = null,
    )
}
