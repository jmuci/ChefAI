package com.tenmilelabs.chefai.mealplans.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.repository.HouseholdRepository
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RemovedMeal
import com.tenmilelabs.chefai.mealplans.domain.print.MealPlanPrintDocument
import com.tenmilelabs.chefai.mealplans.domain.print.MealPlanPrintDocumentBuilder
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListBuilder
import com.tenmilelabs.chefai.mealplans.domain.usecase.GenerateMealPlanUseCase
import com.tenmilelabs.chefai.mealplans.domain.usecase.ObserveSavedRecipesUseCase
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.CancellationException
import com.tenmilelabs.chefai.mealplans.domain.usecase.MealRef
import javax.inject.Inject

sealed interface MealPlanDetailUiState {
    data object Loading : MealPlanDetailUiState
    data object NotFound : MealPlanDetailUiState
    data class Success(
        val mealPlan: MealPlan,
        val board: MealPlanBoard,
        /** True while a generation attempt (remote or local) is in flight. */
        val isGenerating: Boolean = false,
        /** Null unless [mealPlan] is shared and its owner was found in the cached household. */
        val ownerDisplayName: String? = null,
        /** Total shopping-list line count for the footer button — 0 before it has resolved. */
        val shoppingListItemCount: Int = 0,
    ) : MealPlanDetailUiState {
        val meals: List<PlannedMeal> get() = board.meals
        val cookedCount: Int get() = board.cookedCount
        val totalCount: Int get() = board.totalCount
        val progress: Float get() = board.progress

        /** Whether meal names need a "Lunch"/"Dinner" label to be unambiguous. */
        val showsSlotLabels: Boolean
            get() = mealPlan.preferences.mealType == MealType.DINNER_AND_LUNCH
    }
}

sealed interface MealPlanDetailEvent {
    data class ShowError(val message: String) : MealPlanDetailEvent
    data class PrintReady(val document: MealPlanPrintDocument) : MealPlanDetailEvent

    /** The plan was soft-deleted; the screen should leave. */
    data object Deleted : MealPlanDetailEvent

    /**
     * A meal was taken out of the plan; the screen offers an Undo that calls `onUndoRemoveMeal`.
     * [recipeTitle] is `null` when the recipe is not on this device.
     */
    data class MealRemoved(val removed: RemovedMeal, val recipeTitle: String?) : MealPlanDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealPlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealPlanRepository: MealPlanRepository,
    private val recipesRepository: RecipesRepository,
    private val generateMealPlanUseCase: GenerateMealPlanUseCase,
    private val shoppingListRepository: ShoppingListRepository,
    private val householdRepository: HouseholdRepository,
    private val observeSavedRecipes: ObserveSavedRecipesUseCase,
) : ViewModel() {

    private val mealPlanId: UUID = UUID.fromString(
        savedStateHandle.get<String>(AppDestinationArgs.MEAL_PLAN_ID_ARG)
            ?: error("Missing mealPlanId argument")
    )

    private val _events = MutableSharedFlow<MealPlanDetailEvent>()
    val events: SharedFlow<MealPlanDetailEvent> = _events.asSharedFlow()

    private val isGenerating = MutableStateFlow(false)

    val uiState: StateFlow<MealPlanDetailUiState> = mealPlanRepository.observeMealPlan(mealPlanId)
        .flatMapLatest { mealPlan ->
            if (mealPlan == null) {
                flowOf(MealPlanDetailUiState.NotFound)
            } else {
                val recipeIds = mealPlan.days
                    .flatMap { listOfNotNull(it.lunchRecipeId, it.dinnerRecipeId) }
                    .distinct()

                val previews = if (recipeIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    recipesRepository.getRecipePreviewsByIds(recipeIds)
                }

                // A recipe filling two slots needs two shops' worth, matching the shopping-list
                // screen's own count — see ShoppingListViewModel.
                val slotCounts: Map<UUID, Int> = mealPlan.days
                    .flatMap { day -> MealSlot.entries.mapNotNull { day.recipeIdFor(it) } }
                    .groupingBy { it }
                    .eachCount()

                // The footer button's item count is a nice-to-have, not the reason this screen
                // exists — a shopping-list repository hiccup (thrown eagerly or mid-flow) should
                // leave the count at 0 rather than take the whole detail screen down with it.
                val itemCount = if (recipeIds.isEmpty()) {
                    flowOf(0)
                } else {
                    runCatching {
                        // The removed lines are subtracted, so the button agrees with the list it opens.
                        combine(
                            shoppingListRepository.observeIngredientsForRecipes(recipeIds),
                            shoppingListRepository.observeCheckedState(mealPlan.uuid),
                        ) { ingredients, itemState ->
                            ShoppingListBuilder.build(
                                ingredients = ingredients,
                                slotCountByRecipe = slotCounts,
                                plannedServings = mealPlan.preferences.servingsPerMeal,
                                checkedKeys = emptySet(),
                                removedKeys = itemState.removedKeys,
                            ).totalCount
                        }
                    }
                        .getOrElse { flowOf(0) }
                        .catch { emit(0) }
                }

                combine(previews, householdRepository.observeMyHousehold(), itemCount) { list, household, count ->
                    buildState(mealPlan, list.associateBy { it.uuid }, household, count)
                }
            }
        }
        .combine(isGenerating) { state, generating ->
            if (state is MealPlanDetailUiState.Success) state.copy(isGenerating = generating) else state
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MealPlanDetailUiState.Loading,
        )

    /** Drag-and-drop: moves [from] to [to]'s place. Cooked meals are pinned, so the repository ignores them. */
    fun onMoveMeal(from: PlannedMeal, to: PlannedMeal) {
        viewModelScope.launch {
            try {
                mealPlanRepository.moveMeal(
                    mealPlanId,
                    MealRef(from.dayId, from.slot),
                    MealRef(to.dayId, to.slot),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onMoveMeal: failed for plan $mealPlanId")
                _events.emit(MealPlanDetailEvent.ShowError("Couldn't move this meal"))
            }
        }
    }

    /** Empties [meal]'s slot, leaving an "Add recipe" gap, and offers an Undo via [events]. */
    fun onRemoveMeal(meal: PlannedMeal) {
        viewModelScope.launch {
            try {
                val removed = mealPlanRepository.removeMeal(mealPlanId, meal.dayId, meal.slot) ?: return@launch
                _events.emit(MealPlanDetailEvent.MealRemoved(removed, meal.recipe?.title))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onRemoveMeal: failed for day ${meal.dayId} slot ${meal.slot}")
                _events.emit(MealPlanDetailEvent.ShowError("Couldn't remove this meal"))
            }
        }
    }

    /** Puts a meal [onRemoveMeal] took out back; a no-op if the gap was filled in the meantime. */
    fun onUndoRemoveMeal(removed: RemovedMeal) {
        viewModelScope.launch {
            try {
                mealPlanRepository.restoreMeal(removed)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onUndoRemoveMeal: failed for day ${removed.dayId} slot ${removed.slot}")
                _events.emit(MealPlanDetailEvent.ShowError("Couldn't restore this meal"))
            }
        }
    }

    // --- "Add recipe" in an empty slot: pick one of the user's saved recipes for it ---

    private val _addRecipeSlot = MutableStateFlow<BoardSlot.Open?>(null)

    /** The gap whose recipe picker is open, or `null` while it is closed. */
    val addRecipeSlot: StateFlow<BoardSlot.Open?> = _addRecipeSlot

    private val _addRecipeQuery = MutableStateFlow("")

    /** What the user has typed into the picker's search field. */
    val addRecipeQuery: StateFlow<String> = _addRecipeQuery

    /** Saved recipes matching [addRecipeQuery] by title; queried only while the picker is open. */
    val addRecipeRecipes: StateFlow<List<RecipePreview>> = _addRecipeSlot
        .map { it != null }
        .distinctUntilChanged()
        .flatMapLatest { open -> if (open) observeSavedRecipes() else flowOf(emptyList()) }
        .combine(_addRecipeQuery) { recipes, query ->
            val needle = query.trim()
            if (needle.isEmpty()) recipes else recipes.filter { it.title.contains(needle, ignoreCase = true) }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            Timber.e(e, "addRecipeRecipes: failed to load saved recipes")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onAddRecipe(slot: BoardSlot.Open) {
        _addRecipeQuery.value = ""
        _addRecipeSlot.value = slot
    }

    fun onAddRecipeQueryChange(query: String) {
        _addRecipeQuery.value = query
    }

    fun onAddRecipeDismissed() {
        _addRecipeSlot.value = null
    }

    /** Fills the open gap with [recipeId] and closes the picker. */
    fun onAddRecipePicked(recipeId: UUID) {
        val target = _addRecipeSlot.value ?: return
        _addRecipeSlot.value = null
        viewModelScope.launch {
            try {
                mealPlanRepository.assignRecipeToSlot(mealPlanId, target.dayIndex, target.slot, recipeId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onAddRecipePicked: failed for day ${target.dayIndex} slot ${target.slot}")
                _events.emit(MealPlanDetailEvent.ShowError("Couldn't add this recipe"))
            }
        }
    }

    /** Flips a single planned meal between cooked and outstanding. */
    fun onToggleCooked(meal: PlannedMeal) {
        viewModelScope.launch {
            try {
                mealPlanRepository.setMealCooked(meal.dayId, meal.slot, cooked = !meal.isCooked)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onToggleCooked: failed for day ${meal.dayId} slot ${meal.slot}")
                _events.emit(MealPlanDetailEvent.ShowError("Couldn't update this meal"))
            }
        }
    }

    /** Soft-deletes the plan (syncs the deletion) and tells the screen to navigate away via [events]. */
    fun onDeleteConfirmed() {
        viewModelScope.launch {
            try {
                mealPlanRepository.deleteMealPlan(mealPlanId)
                _events.emit(MealPlanDetailEvent.Deleted)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onDeleteConfirmed: failed to delete plan $mealPlanId")
                _events.emit(MealPlanDetailEvent.ShowError("Couldn't delete this meal plan"))
            }
        }
    }

    /** Builds a print-ready document for the current plan and hands it back via [events]. */
    fun onPrintClick() {
        val state = uiState.value
        if (state !is MealPlanDetailUiState.Success || state.totalCount == 0) return

        viewModelScope.launch {
            try {
                val plan = state.mealPlan
                val recipeIds = plan.days
                    .flatMap { listOfNotNull(it.lunchRecipeId, it.dinnerRecipeId) }
                    .distinct()

                val recipeMap = if (recipeIds.isEmpty()) {
                    emptyMap()
                } else {
                    recipesRepository.getRecipePreviewsByIds(recipeIds).first().associateBy { it.uuid }
                }
                val ingredients = if (recipeIds.isEmpty()) {
                    emptyList()
                } else {
                    shoppingListRepository.observeIngredientsForRecipes(recipeIds).first()
                }

                val document = MealPlanPrintDocumentBuilder.build(plan, recipeMap, ingredients)
                _events.emit(MealPlanDetailEvent.PrintReady(document))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onPrintClick: failed to build print document for $mealPlanId")
                _events.emit(MealPlanDetailEvent.ShowError("Couldn't prepare the plan for printing"))
            }
        }
    }

    /**
     * Fills the plan with recipes via [GenerateMealPlanUseCase] — which path runs depends on
     * session type and `recipeSource`, with the on-device scheduler as the universal fallback; see
     * that use case's doc.
     */
    fun onGenerate() {
        if (isGenerating.value) return
        isGenerating.update { true }

        viewModelScope.launch {
            try {
                val plan = mealPlanRepository.observeMealPlan(mealPlanId).first()
                if (plan == null) {
                    _events.emit(MealPlanDetailEvent.ShowError("Meal plan not found"))
                    return@launch
                }

                val filled = generateMealPlanUseCase(mealPlanId, plan.preferences)
                if (!filled) {
                    _events.emit(
                        MealPlanDetailEvent.ShowError(
                            "Save a few recipes first — we build plans from your collection."
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onGenerate: failed for plan $mealPlanId")
                _events.emit(MealPlanDetailEvent.ShowError(e.message ?: "Generation failed"))
            } finally {
                isGenerating.update { false }
            }
        }
    }

    companion object {
        /** Wraps [MealPlanBoard.from] in the screen's success state. */
        internal fun buildState(
            mealPlan: MealPlan,
            recipeMap: Map<UUID, RecipePreview>,
            household: Household? = null,
            shoppingListItemCount: Int = 0,
        ): MealPlanDetailUiState.Success = MealPlanDetailUiState.Success(
            mealPlan = mealPlan,
            board = MealPlanBoard.from(mealPlan, recipeMap),
            ownerDisplayName = if (mealPlan.householdId != null) {
                household?.members?.firstOrNull { it.userId == mealPlan.userId }?.displayName
            } else {
                null
            },
            shoppingListItemCount = shoppingListItemCount,
        )
    }
}
