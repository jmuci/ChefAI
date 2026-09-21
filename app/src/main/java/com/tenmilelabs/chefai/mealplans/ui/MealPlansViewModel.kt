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
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanServingBasis
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanPreferencesRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListBuilder
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

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
        val servingBasis: MealPlanServingBasis,
        val household: Household?,
        val ingredientCount: Int,
    ) : MealPlansUiState {
        val mealCount: Int get() = week.mealCount
        val canSelectFamily: Boolean get() = household != null
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
    private val mealPlanPreferencesRepository: MealPlanPreferencesRepository,
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

    val uiState: StateFlow<MealPlansUiState> = combine(
        plans,
        weekStart,
        mealPlanPreferencesRepository.servingBasis,
        householdRepository.observeMyHousehold(),
    ) { plans, weekStart, basis, household ->
        // "Family" with no household has nothing to filter for and nothing to scale to, so the
        // basis falls back rather than emptying the screen while the toggle claims otherwise.
        val effectiveBasis = if (household == null) MealPlanServingBasis.DEFAULT else basis
        WeekInputs(
            plans = plans.filter { it.matches(effectiveBasis) },
            weekStart = weekStart,
            basis = effectiveBasis,
            household = household,
        )
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
                    servingBasis = inputs.basis,
                    household = inputs.household,
                    ingredientCount = ShoppingListBuilder.build(
                        ingredients = ingredients,
                        slotCountByRecipe = week.slotCountByRecipe,
                        // The basis, not the plan's own servingsPerMeal — a week can span several
                        // plans, and this is what makes their quantities commensurable.
                        plannedServings = inputs.basis.servingsPerMeal(
                            householdSize = inputs.household?.members?.size ?: 0,
                        ),
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

    fun onServingBasisChange(basis: MealPlanServingBasis) {
        viewModelScope.launch { mealPlanPreferencesRepository.setServingBasis(basis) }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private val firstDayOfWeek get() = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    private fun MealPlan.matches(basis: MealPlanServingBasis): Boolean = when (basis) {
        MealPlanServingBasis.JUST_ME -> householdId == null
        MealPlanServingBasis.FAMILY -> householdId != null
    }

    /** Everything the projection needs, gathered before the recipe queries it implies. */
    private inner class WeekInputs(
        val plans: List<MealPlan>,
        val weekStart: LocalDate,
        val basis: MealPlanServingBasis,
        val household: Household?,
    ) {
        fun project(recipes: Map<UUID, RecipePreview>) =
            MealPlanWeekProjector.project(
                plans = plans,
                weekStart = weekStart,
                recipes = recipes,
                zone = clock.zone,
            )
    }
}
