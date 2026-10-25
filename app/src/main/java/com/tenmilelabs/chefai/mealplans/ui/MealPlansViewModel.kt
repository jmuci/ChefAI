package com.tenmilelabs.chefai.mealplans.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.repository.HouseholdRepository
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListBuilder
import com.tenmilelabs.chefai.mealplans.domain.summary.MealPlanSummary
import com.tenmilelabs.chefai.mealplans.domain.summary.MealPlanSummaryBuilder
import com.tenmilelabs.chefai.mealplans.domain.week.DAYS_IN_WEEK
import com.tenmilelabs.chefai.mealplans.domain.week.MealPlanWeek
import com.tenmilelabs.chefai.mealplans.domain.week.MealPlanWeekProjector
import com.tenmilelabs.chefai.mealplans.domain.week.weekStartFor
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

/** Which of the tab's two views is showing. Not persisted — the tab opens on the calendar. */
enum class MealPlansViewMode { WEEK, PLANS }

/** What the plans list shows; separate from the week's state because it spans every plan. */
sealed interface MealPlanListUiState {
    data object Loading : MealPlanListUiState
    data object Error : MealPlanListUiState
    data class Success(
        val plans: List<MealPlanSummary>,
        val today: LocalDate,
    ) : MealPlanListUiState
}

sealed interface MealPlansUiState {
    data object Loading : MealPlansUiState
    data object Error : MealPlansUiState

    /**
     * @property today the device's date, so the week can mark one row as current. Injected through
     *   the clock rather than read in the composable, which keeps the highlight testable.
     * @property ingredientCount distinct shopping-list lines for the whole week. `0` until the
     *   ingredient query resolves, which is also what an empty week reports.
     * @property canGenerateGroceryList true only when the week resolves to exactly one plan — the
     *   shopping list is routed by a plan id. ADR-015 Decision 6.
     */
    data class Success(
        val week: MealPlanWeek,
        val today: LocalDate,
        val ingredientCount: Int,
    ) : MealPlansUiState {
        val mealCount: Int get() = week.mealCount
        val canGenerateGroceryList: Boolean get() = week.singlePlanId != null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealPlansViewModel @Inject constructor(
    mealPlanRepository: MealPlanRepository,
    recipesRepository: RecipesRepository,
    shoppingListRepository: ShoppingListRepository,
    householdRepository: HouseholdRepository,
    userPreferencesRepository: UserPreferencesRepository,
    sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {

    /**
     * The week being read, as its first day.
     *
     * Deliberately not in `SavedStateHandle`: surviving rotation is what a ViewModel is for, and a
     * user returning to a cold app wants this week rather than whichever week they were browsing
     * yesterday. ADR-015 Decision 4.
     */
    private val weekStart = MutableStateFlow(weekStartFor(today(), firstDayOfWeek))

    private val plans: Flow<List<MealPlan>> = sessionManager.userSession
        .flatMapLatest { session ->
            when (session) {
                is UserSession.Loading -> emptyFlow()
                is UserSession.Anonymous ->
                    mealPlanRepository.observeMealPlansForUser(session.localUserId)

                is UserSession.Authenticated ->
                    mealPlanRepository.observeMealPlansForUser(session.user.uuid)
            }
        }

    private val _viewMode = MutableStateFlow(MealPlansViewMode.WEEK)
    val viewMode: StateFlow<MealPlansViewMode> = _viewMode

    /**
     * Every live plan, personal and shared alike: a plan the user invited others to is simply
     * theirs, so there is no "whose plans" switch to filter by.
     */
    val listState: StateFlow<MealPlanListUiState> = plans
        .flatMapLatest { plans ->
            val recipeIds = plans.flatMap { plan ->
                plan.days.flatMap { listOfNotNull(it.lunchRecipeId, it.dinnerRecipeId) }
            }.distinct()
            recipesRepository.getRecipePreviewsByIds(recipeIds).map { previews ->
                val state: MealPlanListUiState = MealPlanListUiState.Success(
                    plans = MealPlanSummaryBuilder.build(
                        plans = plans,
                        recipes = previews.associateBy { it.uuid },
                        zone = clock.zone,
                    ),
                    today = today(),
                )
                state
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(MealPlanListUiState.Error)
        }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = MealPlanListUiState.Loading,
        )

    val uiState: StateFlow<MealPlansUiState> = combine(
        plans,
        weekStart,
        householdRepository.observeMyHousehold(),
    ) { plans, weekStart, household ->
        WeekInputs(plans = plans, weekStart = weekStart, household = household)
    }
        .flatMapLatest { inputs ->
            // The recipes a week needs are only known once the plans are projected onto it, so the
            // projection runs twice: once bare, to learn the ids, and once with the previews. Both
            // passes are pure and in-memory; only the id query in between touches Room.
            val bare = inputs.project(recipes = emptyMap())
            val recipeIds = bare.recipeIds

            combine(
                recipesRepository.getRecipePreviewsByIds(recipeIds),
                shoppingListRepository.observeIngredientsForRecipes(recipeIds),
                userPreferencesRepository.measurementSystem,
            ) { previews, ingredients, measurementSystem ->
                val week = inputs.project(recipes = previews.associateBy { it.uuid })
                val state: MealPlansUiState = MealPlansUiState.Success(
                    week = week,
                    today = today(),
                    ingredientCount = ShoppingListBuilder.build(
                        ingredients = ingredients,
                        slotCountByRecipe = week.slotCountByRecipe,
                        // Not each plan's own servingsPerMeal — a week can span several plans, and
                        // one figure is what makes their quantities commensurable. A week that
                        // includes a shared plan shops for the household; otherwise for one.
                        plannedServings = inputs.plannedServings(week),
                        checkedKeys = emptySet(),
                        measurementSystem = measurementSystem,
                    ).totalCount,
                )
                state
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(MealPlansUiState.Error)
        }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = MealPlansUiState.Loading,
        )

    fun onPreviousWeek() {
        weekStart.update { it.minusDays(DAYS_IN_WEEK.toLong()) }
    }

    fun onNextWeek() {
        weekStart.update { it.plusDays(DAYS_IN_WEEK.toLong()) }
    }

    fun onViewModeChange(mode: MealPlansViewMode) {
        _viewMode.value = mode
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private val firstDayOfWeek get() = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    /** Everything the projection needs, gathered before the recipe queries it implies. */
    private inner class WeekInputs(
        val plans: List<MealPlan>,
        val weekStart: LocalDate,
        val household: Household?,
    ) {
        fun plannedServings(week: MealPlanWeek): Int {
            val sharedInWeek = plans.any { it.uuid in week.planIds && it.householdId != null }
            return if (sharedInWeek) (household?.members?.size ?: 1).coerceAtLeast(1) else 1
        }

        fun project(recipes: Map<UUID, RecipePreview>) =
            MealPlanWeekProjector.project(
                plans = plans,
                weekStart = weekStart,
                recipes = recipes,
                zone = clock.zone,
            )
    }
}
