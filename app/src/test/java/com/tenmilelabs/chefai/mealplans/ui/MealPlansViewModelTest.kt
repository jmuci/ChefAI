package com.tenmilelabs.chefai.mealplans.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.repository.FakeUserPreferencesRepository
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.model.WeekStart
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.household.domain.repository.FakeHouseholdRepository
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
import com.tenmilelabs.chefai.mealplans.domain.week.weekStartFor
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

@ExperimentalCoroutinesApi
class MealPlansViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    /** Wednesday 2026-08-12 — the date the design's own screenshot shows as current. */
    private val today: LocalDate = LocalDate.of(2026, 8, 12)

    /**
     * Derived rather than hard-coded: the screen starts a week on the locale's first day, so a
     * test that assumed Monday would fail on a JVM defaulting to `en-US`, where it is Sunday.
     */
    private val weekStart: LocalDate =
        weekStartFor(today, WeekFields.of(Locale.getDefault()).firstDayOfWeek)

    /**
     * Pinned, so "which week do we open on" and "which row is today" are decided by the test
     * rather than by the day it runs on.
     */
    private val clock: Clock = Clock.fixed(
        today.atStartOfDay(ZoneOffset.UTC).toInstant(),
        ZoneOffset.UTC,
    )

    private lateinit var repository: FakeMealPlanRepository
    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var shoppingListRepository: FakeShoppingListRepository
    private lateinit var householdRepository: FakeHouseholdRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: MealPlansViewModel

    @Before
    fun setup() {
        repository = FakeMealPlanRepository()
        recipesRepository = FakeRecipesRepository()
        shoppingListRepository = FakeShoppingListRepository()
        householdRepository = FakeHouseholdRepository()
        sessionManager = createTestSessionManager(CoroutineScope(mainCoroutineRule.testDispatcher))
        viewModel = buildViewModel()
    }

    private fun buildViewModel(
        preferences: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
    ) = MealPlansViewModel(
        mealPlanRepository = repository,
        recipesRepository = recipesRepository,
        shoppingListRepository = shoppingListRepository,
        householdRepository = householdRepository,
        userPreferencesRepository = preferences,
        sessionManager = sessionManager,
        clock = clock,
    )

    // --- The week ---

    @Test
    fun `opens on the week containing today`() = runTest {
        repository.emitPlans()

        viewModel.uiState.test {
            val state = awaitSuccess()
            assertThat(state.week.weekStart).isEqualTo(weekStart)
            assertThat(state.week.weekEnd).isEqualTo(weekStart.plusDays(6))
            assertThat(state.today).isEqualTo(today)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the week-start setting decides the calendar's first day, live`() = runTest {
        val preferences = FakeUserPreferencesRepository(initialWeekStart = WeekStart.MONDAY)
        val vm = buildViewModel(preferences)
        repository.emitPlans()

        vm.uiState.test {
            assertThat(awaitSuccess().week.weekStart.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)

            preferences.setWeekStart(WeekStart.SUNDAY)

            val state = awaitSuccess()
            assertThat(state.week.weekStart.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
            assertThat(state.week.days).hasSize(7)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty week still reaches Success rather than sticking on Loading`() = runTest {
        repository.emitPlans()

        viewModel.uiState.test {
            val state = awaitSuccess()
            assertThat(state.week.days).hasSize(7)
            assertThat(state.mealCount).isEqualTo(0)
            assertThat(state.ingredientCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a plan's days land on the week`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val recipe = preview("Classic Spaghetti Carbonara")
        recipesRepository.setRecipePreviewsToEmit(listOf(recipe))
        repository.emitPlans(
            makePlan(userId, createdAt = weekStart, days = listOf(day(0, dinner = recipe.uuid))),
        )

        viewModel.uiState.test {
            val state = awaitSuccess { it.mealCount == 1 }
            val meal = state.week.days.first().meals.single()
            assertThat(meal.recipe?.title).isEqualTo("Classic Spaghetti Carbonara")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `next and previous week shift the window by seven days`() = runTest {
        repository.emitPlans()

        viewModel.uiState.test {
            assertThat(awaitSuccess().week.weekStart).isEqualTo(weekStart)

            viewModel.onNextWeek()
            assertThat(awaitSuccess().week.weekStart).isEqualTo(weekStart.plusDays(7))

            viewModel.onPreviousWeek()
            assertThat(awaitSuccess().week.weekStart).isEqualTo(weekStart)

            viewModel.onPreviousWeek()
            assertThat(awaitSuccess().week.weekStart).isEqualTo(weekStart.minusDays(7))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `plans belonging to another user are excluded`() = runTest {
        val recipe = preview("Mine")
        recipesRepository.setRecipePreviewsToEmit(listOf(recipe))
        repository.emitPlans(
            makePlan(UUID.randomUUID(), createdAt = weekStart, days = listOf(day(0, dinner = recipe.uuid))),
        )

        viewModel.uiState.test {
            assertThat(awaitSuccess().mealCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Shared plans ---

    @Test
    fun `personal and household plans both appear in the week`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val mine = preview("Mine")
        val ours = preview("Ours")
        recipesRepository.setRecipePreviewsToEmit(listOf(mine, ours))
        repository.emitPlans(
            makePlan(userId, createdAt = weekStart, days = listOf(day(0, dinner = mine.uuid))),
            makePlan(
                userId,
                createdAt = weekStart,
                days = listOf(day(1, dinner = ours.uuid)),
                householdId = UUID.randomUUID(),
            ),
        )

        viewModel.uiState.test {
            val state = awaitSuccess { it.mealCount == 2 }
            assertThat(state.week.days.flatMap { it.meals }.map { it.recipe?.title })
                .containsExactly("Mine", "Ours")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- The view mode and the plans list ---

    @Test
    fun `opens on the week view and switches to the plans list`() = runTest {
        assertThat(viewModel.viewMode.value).isEqualTo(MealPlansViewMode.WEEK)

        viewModel.onViewModeChange(MealPlansViewMode.PLANS)

        assertThat(viewModel.viewMode.value).isEqualTo(MealPlansViewMode.PLANS)
    }

    @Test
    fun `the plans list covers every plan, newest first, not just this week's`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val old = preview("Old")
        val recent = preview("Recent")
        recipesRepository.setRecipePreviewsToEmit(listOf(old, recent))
        val oldPlan = makePlan(userId, createdAt = weekStart.minusDays(60), days = listOf(day(0, dinner = old.uuid)))
        val recentPlan = makePlan(userId, createdAt = weekStart, days = listOf(day(0, dinner = recent.uuid)))
        repository.emitPlans(oldPlan, recentPlan)

        viewModel.listState.test {
            var state = awaitItem()
            while (state !is MealPlanListUiState.Success || state.plans.size < 2) state = awaitItem()
            assertThat(state.plans.map { it.planId }).containsExactly(recentPlan.uuid, oldPlan.uuid).inOrder()
            assertThat(state.plans.first().recipeTitles).containsExactly("Recent")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- The grocery summary ---

    @Test
    fun `the ingredient count aggregates across the whole week`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val first = preview("First")
        val second = preview("Second")
        recipesRepository.setRecipePreviewsToEmit(listOf(first, second))
        shoppingListRepository.setIngredientsForRecipe(
            first.uuid,
            listOf(ingredient(first.uuid, "Flour", 200.0, "g"), ingredient(first.uuid, "Salt", 1.0, "tsp")),
        )
        shoppingListRepository.setIngredientsForRecipe(
            second.uuid,
            // Flour repeats, so it aggregates onto one line rather than counting twice.
            listOf(ingredient(second.uuid, "Flour", 100.0, "g")),
        )
        repository.emitPlans(
            makePlan(
                userId,
                createdAt = weekStart,
                days = listOf(day(0, dinner = first.uuid), day(1, dinner = second.uuid)),
            ),
        )

        viewModel.uiState.test {
            val state = awaitSuccess { it.ingredientCount > 0 }
            assertThat(state.mealCount).isEqualTo(2)
            assertThat(state.ingredientCount).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `generating a grocery list needs exactly one plan`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val recipe = preview("Only")
        recipesRepository.setRecipePreviewsToEmit(listOf(recipe))
        val plan = makePlan(userId, createdAt = weekStart, days = listOf(day(0, dinner = recipe.uuid)))
        repository.emitPlans(plan)

        viewModel.uiState.test {
            val state = awaitSuccess { it.mealCount > 0 }
            assertThat(state.canGenerateGroceryList).isTrue()
            assertThat(state.week.singlePlanId).isEqualTo(plan.uuid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty week cannot generate a grocery list`() = runTest {
        repository.emitPlans()

        viewModel.uiState.test {
            assertThat(awaitSuccess().canGenerateGroceryList).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a week spanning two plans cannot generate a grocery list`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val a = preview("A")
        val b = preview("B")
        recipesRepository.setRecipePreviewsToEmit(listOf(a, b))
        repository.emitPlans(
            makePlan(userId, createdAt = weekStart, days = listOf(day(0, dinner = a.uuid))),
            makePlan(userId, createdAt = weekStart.plusDays(3), days = listOf(day(0, dinner = b.uuid))),
        )

        viewModel.uiState.test {
            val state = awaitSuccess { it.mealCount == 2 }
            assertThat(state.week.planIds).hasSize(2)
            assertThat(state.canGenerateGroceryList).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Error state ---

    @Test
    fun `uiState is Error when the plan repository throws`() = runTest {
        repository = FakeMealPlanRepository().also { it.shouldThrowOnObserve = true }
        viewModel = buildViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state is MealPlansUiState.Loading) state = awaitItem()
            assertThat(state).isInstanceOf(MealPlansUiState.Error::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Helpers ---

    /**
     * Skips `Loading` and, optionally, the intermediate `Success` states the layered flows emit
     * while the recipe and ingredient queries resolve.
     */
    private suspend fun app.cash.turbine.TurbineTestContext<MealPlansUiState>.awaitSuccess(
        until: (MealPlansUiState.Success) -> Boolean = { true },
    ): MealPlansUiState.Success {
        while (true) {
            val state = awaitItem()
            if (state is MealPlansUiState.Success && until(state)) return state
        }
    }

    private fun day(index: Int, dinner: UUID? = null, lunch: UUID? = null) = MealPlanDay(
        uuid = UUID.randomUUID(),
        dayIndex = index,
        dinnerRecipeId = dinner,
        lunchRecipeId = lunch,
    )

    private fun makePlan(
        userId: UUID,
        createdAt: LocalDate,
        days: List<MealPlanDay>,
        householdId: UUID? = null,
    ): MealPlan {
        val millis = createdAt.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return MealPlan(
            uuid = UUID.randomUUID(),
            userId = userId,
            name = "Week plan",
            preferences = MealPlanPreferences(
                planLengthDays = days.size,
                mealType = MealType.DINNER,
                dietaryRestrictions = emptySet<DietaryRestriction>(),
                recipeSource = RecipeSource.COLLECTION_ONLY,
                maxPrepTimeMinutes = null,
                servingsPerMeal = 2,
                batchCooking = false,
                leftoverFriendly = false,
                varietyPreference = VarietyPreference.MEDIUM,
            ),
            status = MealPlanStatus.READY,
            createdAt = millis,
            updatedAt = millis,
            days = days,
            householdId = householdId,
        )
    }

    private fun preview(title: String) = RecipePreview(
        uuid = UUID.randomUUID(),
        title = title,
        description = "",
        imageUrlThumbnail = "",
        prepTimeMinutes = 10,
        cookTimeMinutes = 15,
        servings = 4,
        creatorId = UUID.randomUUID(),
        tags = emptyList(),
        labels = emptyList(),
    )

    private fun ingredient(recipeId: UUID, name: String, quantity: Double, unit: String) =
        PlannedIngredient(
            recipeId = recipeId,
            displayName = name,
            quantity = quantity,
            unit = unit,
            recipeServings = 4,
        )
}
