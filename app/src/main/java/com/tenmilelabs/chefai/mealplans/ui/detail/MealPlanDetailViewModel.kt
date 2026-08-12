package com.tenmilelabs.chefai.mealplans.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.core.data.sync.SyncExecutor
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

sealed interface MealPlanDetailUiState {
    data object Loading : MealPlanDetailUiState
    data object NotFound : MealPlanDetailUiState
    data class Success(
        val mealPlan: MealPlan,
        val dayItems: List<DayItem>,
        val isGenerating: Boolean = false,
    ) : MealPlanDetailUiState
}

sealed interface MealPlanDetailEvent {
    data class ShowError(val message: String) : MealPlanDetailEvent
}

/**
 * A flattened item for the lazy list: either a day header or a recipe card.
 */
sealed interface DayItem {
    data class Header(val label: String) : DayItem
    data class RecipeCard(
        val recipe: RecipePreview,
        val recipeId: UUID,
    ) : DayItem
    data class MissingRecipe(val recipeId: UUID) : DayItem
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealPlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealPlanRepository: MealPlanRepository,
    recipesRepository: RecipesRepository,
    private val syncExecutor: SyncExecutor,
) : ViewModel() {

    private val mealPlanId: UUID = UUID.fromString(
        savedStateHandle.get<String>(AppDestinationArgs.MEAL_PLAN_ID_ARG)
            ?: error("Missing mealPlanId argument")
    )

    private val _events = MutableSharedFlow<MealPlanDetailEvent>()
    val events: SharedFlow<MealPlanDetailEvent> = _events.asSharedFlow()

    val uiState: StateFlow<MealPlanDetailUiState> = mealPlanRepository.observeMealPlan(mealPlanId)
        .flatMapLatest { mealPlan ->
            if (mealPlan == null) {
                flowOf(MealPlanDetailUiState.NotFound)
            } else {
                val allRecipeIds = mealPlan.days.flatMap { day ->
                    listOfNotNull(day.lunchRecipeId, day.dinnerRecipeId)
                }.distinct()

                if (allRecipeIds.isEmpty()) {
                    flowOf(
                        MealPlanDetailUiState.Success(
                            mealPlan = mealPlan,
                            dayItems = buildDayItems(mealPlan, emptyMap()),
                        )
                    )
                } else {
                    recipesRepository.getRecipePreviewsByIds(allRecipeIds)
                        .map { previews ->
                            val previewMap = previews.associateBy { it.uuid }
                            MealPlanDetailUiState.Success(
                                mealPlan = mealPlan,
                                dayItems = buildDayItems(mealPlan, previewMap),
                            )
                        }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MealPlanDetailUiState.Loading,
        )

    fun onGenerate() {
        viewModelScope.launch {
            try {
                // 1. Push so the server has the meal plan (suspends until complete)
                Timber.d("onGenerate: pushing meal plan $mealPlanId to server")
                syncExecutor.sync()

                // 2. Call the generate endpoint
                Timber.d("onGenerate: calling generate API for $mealPlanId")
                mealPlanRepository.requestGeneration(mealPlanId)
                    .getOrThrow()

                // 3. Wait briefly for server-side generation to complete, then pull results.
                //    Generation is fast (~100ms) but async on the server.
                delay(GENERATION_POLL_DELAY_MS)
                Timber.d("onGenerate: pulling generated results for $mealPlanId")
                syncExecutor.sync()
            } catch (e: Exception) {
                Timber.e(e, "onGenerate: failed for plan $mealPlanId")
                _events.emit(MealPlanDetailEvent.ShowError(e.message ?: "Generation failed"))
            }
        }
    }

    companion object {
        /** Delay before pulling to let server-side generation finish. */
        private const val GENERATION_POLL_DELAY_MS = 2_000L

        internal fun buildDayItems(
            mealPlan: MealPlan,
            recipeMap: Map<UUID, RecipePreview>,
        ): List<DayItem> {
            val items = mutableListOf<DayItem>()
            val isSingleMealType = mealPlan.preferences.mealType == MealType.DINNER

            for (day in mealPlan.days.sortedBy { it.dayIndex }) {
                val dayNumber = day.dayIndex + 1

                if (isSingleMealType) {
                    // Single meal type: "Day 1"
                    items.add(DayItem.Header("Day $dayNumber"))
                    val recipeId = day.dinnerRecipeId ?: day.lunchRecipeId
                    if (recipeId != null) {
                        val preview = recipeMap[recipeId]
                        if (preview != null) {
                            items.add(DayItem.RecipeCard(recipe = preview, recipeId = recipeId))
                        } else {
                            items.add(DayItem.MissingRecipe(recipeId))
                        }
                    }
                } else {
                    // Multiple meal types: separate headers per meal
                    if (day.lunchRecipeId != null) {
                        items.add(DayItem.Header("Day $dayNumber — Lunch"))
                        val preview = recipeMap[day.lunchRecipeId]
                        if (preview != null) {
                            items.add(DayItem.RecipeCard(recipe = preview, recipeId = day.lunchRecipeId))
                        } else {
                            items.add(DayItem.MissingRecipe(day.lunchRecipeId))
                        }
                    }
                    if (day.dinnerRecipeId != null) {
                        items.add(DayItem.Header("Day $dayNumber — Dinner"))
                        val preview = recipeMap[day.dinnerRecipeId]
                        if (preview != null) {
                            items.add(DayItem.RecipeCard(recipe = preview, recipeId = day.dinnerRecipeId))
                        } else {
                            items.add(DayItem.MissingRecipe(day.dinnerRecipeId))
                        }
                    }
                }
            }
            return items
        }
    }
}
