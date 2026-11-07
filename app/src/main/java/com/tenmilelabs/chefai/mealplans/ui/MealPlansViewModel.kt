package com.tenmilelabs.chefai.mealplans.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.repository.HouseholdRepository
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.SavedMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.usecase.MealRef
import com.tenmilelabs.chefai.mealplans.domain.usecase.RepeatSavedMealPlanUseCase
import com.tenmilelabs.chefai.mealplans.domain.week.MealPlanOverlap
import com.tenmilelabs.chefai.mealplans.domain.week.defaultMealPlanStartDate
import kotlinx.coroutines.flow.first
import com.tenmilelabs.chefai.mealplans.domain.week.PlannedWeekMeal
import com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListBuilder
import com.tenmilelabs.chefai.mealplans.domain.summary.MealPlanSummary
import com.tenmilelabs.chefai.mealplans.domain.summary.MealPlanSummaryBuilder
import com.tenmilelabs.chefai.mealplans.domain.summary.SavedMealPlanSummary
import com.tenmilelabs.chefai.mealplans.domain.summary.SavedMealPlanSummaryBuilder
import com.tenmilelabs.chefai.mealplans.domain.week.DAYS_IN_WEEK
import com.tenmilelabs.chefai.mealplans.domain.week.MealPlanWeek
import com.tenmilelabs.chefai.mealplans.domain.week.MealPlanWeekProjector
import com.tenmilelabs.chefai.mealplans.domain.week.weekStartFor
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

/** Which of the tab's two views is showing. Not persisted — the tab opens on the calendar. */
enum class MealPlansViewMode { WEEK, PLANS }

/** The plans list's two tabs. "Saved" only exists while at least one saved plan does. */
enum class PlanListTab { SCHEDULED, SAVED }

/** What the plans list shows; separate from the week's state because it spans every plan. */
sealed interface MealPlanListUiState {
    data object Loading : MealPlanListUiState
    data object Error : MealPlanListUiState
    /**
     * @property savedPlans the user's saved templates, newest first.
     * @property selectedTab the tab on screen. Always [PlanListTab.SCHEDULED] while [savedPlans] is
     *   empty, so deleting the last saved plan can never strand the user on a vanished tab.
     */
    data class Success(
        val plans: List<MealPlanSummary>,
        val today: LocalDate,
        val savedPlans: List<SavedMealPlanSummary> = emptyList(),
        val selectedTab: PlanListTab = PlanListTab.SCHEDULED,
    ) : MealPlanListUiState
}

/** One-shot messages for the tab; the screen turns them into snackbars. */
sealed interface MealPlansEvent {
    data class ShowMessage(@StringRes val messageRes: Int) : MealPlansEvent

    /**
     * A saved plan was scheduled: open [planId]. [overlappingPlanName] is the existing plan its
     * dates collide with, if any — a non-blocking notice, computed before the plan was created.
     */
    data class NavigateToPlan(val planId: UUID, val overlappingPlanName: String?) : MealPlansEvent
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
    private val mealPlanRepository: MealPlanRepository,
    private val savedMealPlanRepository: SavedMealPlanRepository,
    private val recipesRepository: RecipesRepository,
    private val collectionsRepository: CollectionsRepository,
    shoppingListRepository: ShoppingListRepository,
    householdRepository: HouseholdRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val sessionManager: SessionManager,
    private val repeatSavedMealPlan: RepeatSavedMealPlanUseCase,
    private val clock: Clock,
) : ViewModel() {

    /**
     * A date inside the week being read; the week's first day follows the user's week-start setting.
     *
     * Deliberately not in `SavedStateHandle`: surviving rotation is what a ViewModel is for, and a
     * user returning to a cold app wants this week rather than whichever week they were browsing
     * yesterday. ADR-015 Decision 4.
     */
    private val browsedDate = MutableStateFlow(today())

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

    private val savedPlans: Flow<List<SavedMealPlan>> = sessionManager.userSession
        .flatMapLatest { session ->
            when (session) {
                is UserSession.Loading -> emptyFlow()
                is UserSession.Anonymous -> savedMealPlanRepository.observeSavedPlans(session.localUserId)
                is UserSession.Authenticated -> savedMealPlanRepository.observeSavedPlans(session.user.uuid)
            }
        }

    private val _planListTab = MutableStateFlow(PlanListTab.SCHEDULED)

    private val _viewMode = MutableStateFlow(MealPlansViewMode.WEEK)
    val viewMode: StateFlow<MealPlansViewMode> = _viewMode

    /**
     * Every live plan, personal and shared alike: a plan the user invited others to is simply
     * theirs, so there is no "whose plans" switch to filter by.
     */
    val listState: StateFlow<MealPlanListUiState> = combine(plans, savedPlans, _planListTab) { plans, saved, tab ->
        PlanListInputs(plans, saved, tab)
    }
        .flatMapLatest { inputs ->
            // One query for both lists: the saved plans' recipes are usually the scheduled ones'.
            val recipeIds = (
                inputs.plans.flatMap { plan ->
                    plan.days.flatMap { listOfNotNull(it.lunchRecipeId, it.dinnerRecipeId) }
                } + inputs.saved.flatMap { plan ->
                    plan.days.flatMap { listOfNotNull(it.lunchRecipeId, it.dinnerRecipeId) }
                }
                ).distinct()
            recipesRepository.getRecipePreviewsByIds(recipeIds).map { previews ->
                val byId = previews.associateBy { it.uuid }
                val state: MealPlanListUiState = MealPlanListUiState.Success(
                    plans = MealPlanSummaryBuilder.build(
                        plans = inputs.plans,
                        recipes = byId,
                        zone = clock.zone,
                    ),
                    today = today(),
                    savedPlans = SavedMealPlanSummaryBuilder.build(inputs.saved, byId),
                    selectedTab = if (inputs.saved.isEmpty()) PlanListTab.SCHEDULED else inputs.tab,
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
        browsedDate,
        userPreferencesRepository.weekStart,
        householdRepository.observeMyHousehold(),
    ) { plans, date, firstDay, household ->
        WeekInputs(
            plans = plans,
            weekStart = weekStartFor(date, firstDay.dayOfWeek),
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
        browsedDate.update { it.minusDays(DAYS_IN_WEEK.toLong()) }
    }

    fun onNextWeek() {
        browsedDate.update { it.plusDays(DAYS_IN_WEEK.toLong()) }
    }

    /**
     * The first day of the week on screen, or `null` when the plans list is showing (or the week
     * has not loaded) — what the "+" hands the wizard to default its start date within this week.
     */
    fun browsedWeekStart(): LocalDate? =
        if (_viewMode.value == MealPlansViewMode.WEEK) {
            (uiState.value as? MealPlansUiState.Success)?.week?.weekStart
        } else {
            null
        }

    fun onPlanListTabSelected(tab: PlanListTab) {
        _planListTab.value = tab
    }

    fun onViewModeChange(mode: MealPlansViewMode) {
        _viewMode.value = mode
    }

    // --- "+ Add meal": pick a recipe for a date; the nav layer opens the wizard with it ---

    private val _addMealDate = MutableStateFlow<LocalDate?>(null)

    /** The date whose "Add meal" sheet is open, or `null` while it is closed. */
    val addMealDate: StateFlow<LocalDate?> = _addMealDate

    private val _addMealQuery = MutableStateFlow("")

    /** What the user has typed into the sheet's search field. */
    val addMealQuery: StateFlow<String> = _addMealQuery

    /**
     * The user's saved recipes matching [addMealQuery], by title — every saved recipe while the
     * query is blank. The same set the Recipes tab shows (bookmarks), never the public catalog.
     * Queried only while the sheet is open; empty otherwise.
     */
    val addMealRecipes: StateFlow<List<RecipePreview>> = _addMealDate
        .map { it != null }
        .distinctUntilChanged()
        .flatMapLatest { open -> if (open) savedRecipes() else flowOf(emptyList()) }
        .combine(_addMealQuery) { recipes, query ->
            val needle = query.trim()
            if (needle.isEmpty()) recipes else recipes.filter { it.title.contains(needle, ignoreCase = true) }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            Timber.e(e, "addMealRecipes: failed to load saved recipes")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = emptyList(),
        )

    private val _events = Channel<MealPlansEvent>(Channel.BUFFERED)
    val events: Flow<MealPlansEvent> = _events.receiveAsFlow()

    private fun savedRecipes(): Flow<List<RecipePreview>> = sessionManager.userSession
        .flatMapLatest { session ->
            when (session) {
                is UserSession.Loading -> emptyFlow()
                is UserSession.Anonymous -> collectionsRepository.observeBookmarkedRecipeIds(session.localUserId)
                is UserSession.Authenticated -> collectionsRepository.observeBookmarkedRecipeIds(session.user.uuid)
            }
        }
        .flatMapLatest { ids -> recipesRepository.getRecipePreviewsByIds(ids.toList()) }
        .map { recipes -> recipes.sortedBy { it.title.lowercase() } }

    fun onAddMeal(date: LocalDate) {
        _addMealQuery.value = ""
        _addMealDate.value = date
    }

    fun onAddMealQueryChange(query: String) {
        _addMealQuery.value = query
    }

    /**
     * Drag-and-drop in the week view: moves [from] to where [to] sits. Only meals of the same plan
     * can be exchanged — a cross-plan drop has no defined meaning, so it is ignored.
     */
    fun onMoveMeal(from: PlannedWeekMeal, to: PlannedWeekMeal) {
        if (from.planId != to.planId) return
        viewModelScope.launch {
            try {
                mealPlanRepository.moveMeal(
                    from.planId,
                    MealRef(from.dayId, from.slot),
                    MealRef(to.dayId, to.slot),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "onMoveMeal: failed for plan ${from.planId}")
                _events.send(MealPlansEvent.ShowMessage(R.string.meal_plans_move_failed))
            }
        }
    }

    // --- Deleting a plan from the plans list ---

    private val _pendingDeletePlanId = MutableStateFlow<UUID?>(null)

    /** The plan whose delete confirmation is open, or `null` while it is closed. */
    val pendingDeletePlanId: StateFlow<UUID?> = _pendingDeletePlanId

    fun onDeletePlanRequested(planId: UUID) {
        _pendingDeletePlanId.value = planId
    }

    fun onDeletePlanDismissed() {
        _pendingDeletePlanId.value = null
    }

    /** Soft-deletes the confirmed plan (the deletion syncs); the list drops it via its Room flow. */
    fun onDeletePlanConfirmed() {
        val planId = _pendingDeletePlanId.value ?: return
        _pendingDeletePlanId.value = null
        viewModelScope.launch {
            try {
                mealPlanRepository.deleteMealPlan(planId)
                _events.send(MealPlansEvent.ShowMessage(R.string.meal_plan_deleted))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "onDeletePlanConfirmed: failed for plan $planId")
                _events.send(MealPlansEvent.ShowMessage(R.string.meal_plan_delete_failed))
            }
        }
    }

    // --- Saved plans (the "Saved" tab) ---

    private val _pendingDeleteSavedPlanId = MutableStateFlow<UUID?>(null)

    /** The saved plan whose delete confirmation is open, or `null` while it is closed. */
    val pendingDeleteSavedPlanId: StateFlow<UUID?> = _pendingDeleteSavedPlanId

    fun onDeleteSavedPlanRequested(savedPlanId: UUID) {
        _pendingDeleteSavedPlanId.value = savedPlanId
    }

    fun onDeleteSavedPlanDismissed() {
        _pendingDeleteSavedPlanId.value = null
    }

    /** Soft-deletes the confirmed saved plan (and its photo); the Saved tab drops it via Room. */
    fun onDeleteSavedPlanConfirmed() {
        val savedPlanId = _pendingDeleteSavedPlanId.value ?: return
        _pendingDeleteSavedPlanId.value = null
        viewModelScope.launch {
            try {
                savedMealPlanRepository.delete(savedPlanId)
                _events.send(MealPlansEvent.ShowMessage(R.string.saved_plan_deleted))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "onDeleteSavedPlanConfirmed: failed for saved plan $savedPlanId")
                _events.send(MealPlansEvent.ShowMessage(R.string.saved_plan_delete_failed))
            }
        }
    }

    private val _pendingRepeatSavedPlanId = MutableStateFlow<UUID?>(null)

    /** The saved plan the user asked to repeat (its date picker is open), or `null`. */
    val pendingRepeatSavedPlanId: StateFlow<UUID?> = _pendingRepeatSavedPlanId

    /**
     * The date the repeat picker opens on: the day after the last planned meal, never before today.
     * Same rule as the wizard's default.
     */
    val repeatDefaultDate: StateFlow<LocalDate> = plans
        .map { defaultMealPlanStartDate(it, today(), clock.zone) }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(today())
        }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = today(),
        )

    private var isRepeating = false

    /** Schedules the pending saved plan on [date], then asks the screen to open the new plan. */
    fun onRepeatSavedPlanConfirmed(date: LocalDate) {
        val savedPlanId = _pendingRepeatSavedPlanId.value ?: return
        _pendingRepeatSavedPlanId.value = null
        if (isRepeating) return
        isRepeating = true
        viewModelScope.launch {
            try {
                val userId = sessionManager.getCurrentUserId()
                val template = savedMealPlanRepository.getSavedPlan(savedPlanId)
                if (userId == null || template == null) {
                    _events.send(MealPlansEvent.ShowMessage(R.string.saved_plan_repeat_failed))
                    return@launch
                }
                // Read before the new plan exists, so it cannot overlap itself.
                val overlappingPlanName = findOverlappingPlanName(
                    userId = userId,
                    start = date,
                    lengthDays = template.preferences.planLengthDays,
                )
                repeatSavedMealPlan(userId, savedPlanId, date).fold(
                    onSuccess = { planId ->
                        _events.send(MealPlansEvent.NavigateToPlan(planId, overlappingPlanName))
                    },
                    onFailure = {
                        _events.send(MealPlansEvent.ShowMessage(R.string.saved_plan_repeat_failed))
                    },
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onRepeatSavedPlanConfirmed: failed for saved plan $savedPlanId")
                _events.send(MealPlansEvent.ShowMessage(R.string.saved_plan_repeat_failed))
            } finally {
                isRepeating = false
            }
        }
    }

    /** The name of the oldest live plan the new dates would share, or `null`; a failed read is "none". */
    private suspend fun findOverlappingPlanName(userId: UUID, start: LocalDate, lengthDays: Int): String? =
        try {
            MealPlanOverlap.overlapping(
                plans = mealPlanRepository.observeMealPlansForUser(userId).first(),
                start = start,
                lengthDays = lengthDays,
                zone = clock.zone,
            ).minByOrNull { it.createdAt }?.name
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "Could not check for overlapping meal plans")
            null
        }

    fun onRepeatSavedPlanRequested(savedPlanId: UUID) {
        _pendingRepeatSavedPlanId.value = savedPlanId
    }

    fun onRepeatSavedPlanDismissed() {
        _pendingRepeatSavedPlanId.value = null
    }

    fun onAddMealDismissed() {
        _addMealDate.value = null
        _addMealQuery.value = ""
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private class PlanListInputs(
        val plans: List<MealPlan>,
        val saved: List<SavedMealPlan>,
        val tab: PlanListTab,
    )

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
