package com.tenmilelabs.chefai.mealplans.ui.shoppinglist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingList
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListBuilder
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

sealed interface ShoppingListUiState {
    data object Loading : ShoppingListUiState
    data object NotFound : ShoppingListUiState
    data class Success(
        val planName: String,
        val list: ShoppingList,
    ) : ShoppingListUiState
}

sealed interface ShoppingListEvent {
    data class ShowError(val message: String) : ShoppingListEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealPlanRepository: MealPlanRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val mealPlanId: UUID = UUID.fromString(
        savedStateHandle.get<String>(AppDestinationArgs.MEAL_PLAN_ID_ARG)
            ?: error("Missing mealPlanId argument")
    )

    private val _events = MutableSharedFlow<ShoppingListEvent>()
    val events: SharedFlow<ShoppingListEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ShoppingListUiState> = mealPlanRepository.observeMealPlan(mealPlanId)
        .flatMapLatest { plan ->
            if (plan == null) {
                flowOf(ShoppingListUiState.NotFound)
            } else {
                // A recipe filling two slots needs two shops' worth, so count slots rather than
                // deduplicating to a plain id list.
                val slotCounts: Map<UUID, Int> = plan.days
                    .flatMap { day -> MealSlot.entries.mapNotNull { day.recipeIdFor(it) } }
                    .groupingBy { it }
                    .eachCount()

                combine(
                    shoppingListRepository.observeIngredientsForRecipes(slotCounts.keys.toList()),
                    shoppingListRepository.observeCheckedItems(mealPlanId),
                    userPreferencesRepository.measurementSystem,
                ) { ingredients, checked, measurementSystem ->
                    ShoppingListUiState.Success(
                        planName = plan.name,
                        list = ShoppingListBuilder.build(
                            ingredients = ingredients,
                            slotCountByRecipe = slotCounts,
                            plannedServings = plan.preferences.servingsPerMeal,
                            checkedKeys = checked,
                            measurementSystem = measurementSystem,
                        ),
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShoppingListUiState.Loading,
        )

    fun onToggleItem(item: ShoppingListItem) {
        viewModelScope.launch {
            try {
                shoppingListRepository.setChecked(mealPlanId, item.key, checked = !item.isChecked)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onToggleItem: failed for item ${item.key}")
                _events.emit(ShoppingListEvent.ShowError("Couldn't update this item"))
            }
        }
    }

    fun onUncheckAll() {
        viewModelScope.launch {
            try {
                shoppingListRepository.clearChecks(mealPlanId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onUncheckAll: failed for plan $mealPlanId")
                _events.emit(ShoppingListEvent.ShowError("Couldn't clear the list"))
            }
        }
    }
}
