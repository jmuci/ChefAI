package com.tenmilelabs.chefai.mealplans.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.repository.FakeUserPreferencesRepository
import com.tenmilelabs.chefai.core.domain.model.HouseholdRole
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.model.HouseholdMember
import com.tenmilelabs.chefai.household.domain.repository.FakeHouseholdRepository
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanPreferencesRepository
import com.tenmilelabs.chefai.mealplans.data.repository.FakeMealPlanRepository
import com.tenmilelabs.chefai.mealplans.data.repository.FakeShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanServingBasis
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
    private lateinit var mealPlanPreferences: FakeMealPlanPreferencesRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: MealPlansViewModel

    @Before
    fun setup() {
        repository = FakeMealPlanRepository()
        recipesRepository = FakeRecipesRepository()
        shoppingListRepository = FakeShoppingListRepository()
        householdRepository = FakeHouseholdRepository()
        mealPlanPreferences = FakeMealPlanPreferencesRepository()
        sessionManager = createTestSessionManager(CoroutineScope(mainCoroutineRule.testDispatcher))
        viewModel = buildViewModel()
    }

    private fun buildViewModel() = MealPlansViewModel(
        mealPlanRepository = repository,
        recipesRepository = recipesRepository,
        shoppingListRepository = shoppingListRepository,
        householdRepository = householdRepository,
        mealPlanPreferencesRepository = mealPlanPreferences,
        userPreferencesRepository = FakeUserPreferencesRepository(),
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

    // --- The serving basis ---

    @Test
    fun `just me shows personal plans and hides household ones`() = runTest {
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
            val state = awaitSuccess { it.mealCount > 0 }
            assertThat(state.servingBasis).isEqualTo(MealPlanServingBasis.JUST_ME)
            assertThat(state.week.days.flatMap { it.meals }.map { it.recipe?.title })
                .containsExactly("Mine")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `family shows household plans once a household is cached`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        val household = household(userId, size = 4)
        householdRepository.seedHousehold(household)
        mealPlanPreferences = FakeMealPlanPreferencesRepository(MealPlanServingBasis.FAMILY)
        viewModel = buildViewModel()

        val mine = preview("Mine")
        val ours = preview("Ours")
        recipesRepository.setRecipePreviewsToEmit(listOf(mine, ours))
        repository.emitPlans(
            makePlan(userId, createdAt = weekStart, days = listOf(day(0, dinner = mine.uuid))),
            makePlan(
                userId,
                createdAt = weekStart,
                days = listOf(day(1, dinner = ours.uuid)),
                householdId = household.uuid,
            ),
        )

        viewModel.uiState.test {
            val state = awaitSuccess { it.mealCount > 0 }
            assertThat(state.week.days.flatMap { it.meals }.map { it.recipe?.title })
                .containsExactly("Ours")
            assertThat(state.canSelectFamily).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `family falls back to just me when there is no household`() = runTest {
        val userId = sessionManager.getCurrentUserId()!!
        mealPlanPreferences = FakeMealPlanPreferencesRepository(MealPlanServingBasis.FAMILY)
        viewModel = buildViewModel()

        val mine = preview("Mine")
        recipesRepository.setRecipePreviewsToEmit(listOf(mine))
        repository.emitPlans(
            makePlan(userId, createdAt = weekStart, days = listOf(day(0, dinner = mine.uuid))),
        )

        viewModel.uiState.test {
            val state = awaitSuccess { it.mealCount > 0 }
            // Family with nothing to switch to would otherwise empty the screen while the toggle
            // claimed a household existed.
            assertThat(state.servingBasis).isEqualTo(MealPlanServingBasis.JUST_ME)
            assertThat(state.canSelectFamily).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onServingBasisChange persists the choice`() = runTest {
        repository.emitPlans()

        viewModel.onServingBasisChange(MealPlanServingBasis.FAMILY)

        assertThat(mealPlanPreferences.current).isEqualTo(MealPlanServingBasis.FAMILY)
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

    private fun household(userId: UUID, size: Int) = Household(
        uuid = UUID.randomUUID(),
        name = "The Test Kitchen",
        ownerId = userId,
        members = (0 until size).map { index ->
            HouseholdMember(
                if (index == 0) userId else UUID.randomUUID(),
                "Member $index",
                "",
                HouseholdRole.OWNER,
            )
        },
    )

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
