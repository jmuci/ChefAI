package com.tenmilelabs.chefai.mealplans.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.print.MealPlanPrintDocument
import com.tenmilelabs.chefai.mealplans.domain.print.MealPlanPrintDocumentBuilder
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.usecase.GenerateMealPlanUseCase
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
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
import javax.inject.Inject

sealed interface MealPlanDetailUiState {
    data object Loading : MealPlanDetailUiState
    data object NotFound : MealPlanDetailUiState
    data class Success(
        val mealPlan: MealPlan,
        val board: MealPlanBoard,
        /** True while a generation attempt (remote or local) is in flight. */
        val isGenerating: Boolean = false,
    ) : MealPlanDetailUiState {
        val upcoming: List<DaySection> get() = board.upcoming
        val cooked: List<PlannedMeal> get() = board.cooked
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
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealPlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealPlanRepository: MealPlanRepository,
    private val recipesRepository: RecipesRepository,
    private val generateMealPlanUseCase: GenerateMealPlanUseCase,
    private val shoppingListRepository: ShoppingListRepository,
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

                previews.map { list -> buildState(mealPlan, list.associateBy { it.uuid }) }
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
        ): MealPlanDetailUiState.Success = MealPlanDetailUiState.Success(
            mealPlan = mealPlan,
            board = MealPlanBoard.from(mealPlan, recipeMap),
        )
    }
}
