package com.tenmilelabs.chefai.mealplans.ui.shoppinglist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.household.domain.repository.HouseholdRepository
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.ShoppingListRepository
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingList
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListBuilder
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
        /** Null unless the plan is shared and its owner was found in the cached household. */
        val ownerDisplayName: String? = null,
    ) : ShoppingListUiState
}

sealed interface ShoppingListEvent {
    data class ShowError(val message: String) : ShoppingListEvent

    /** A line was just removed; the screen offers an Undo that calls `onUndoRemove(itemKey)`. */
    data class ItemRemoved(val itemKey: String, val displayName: String) : ShoppingListEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealPlanRepository: MealPlanRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val householdRepository: HouseholdRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val mealPlanId: UUID = UUID.fromString(
        savedStateHandle.get<String>(AppDestinationArgs.MEAL_PLAN_ID_ARG)
            ?: error("Missing mealPlanId argument")
    )

    private val _events = MutableSharedFlow<ShoppingListEvent>()
    val events: SharedFlow<ShoppingListEvent> = _events.asSharedFlow()

    private val _draft = MutableStateFlow("")

    /** The text in the "add an item" box. Kept out of [uiState] so typing doesn't rebuild the list. */
    val draft: StateFlow<String> = _draft.asStateFlow()

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
                    shoppingListRepository.observeCheckedState(mealPlanId),
                    userPreferencesRepository.measurementSystem,
                    householdRepository.observeMyHousehold(),
                    shoppingListRepository.observeCustomItems(mealPlanId),
                ) { ingredients, checkedState, measurementSystem, household, customItems ->
                    val members = household?.members.orEmpty()
                    ShoppingListUiState.Success(
                        planName = plan.name,
                        list = ShoppingListBuilder.build(
                            ingredients = ingredients,
                            slotCountByRecipe = slotCounts,
                            plannedServings = plan.preferences.servingsPerMeal,
                            checkedKeys = checkedState.checkedKeys,
                            measurementSystem = measurementSystem,
                            checkedByNames = checkedState.checkedByUserIds.mapNotNull { (itemKey, userId) ->
                                members.firstOrNull { it.userId == userId }?.displayName?.let { itemKey to it }
                            }.toMap(),
                            displayNameOverrides = checkedState.displayNameOverrides,
                            removedKeys = checkedState.removedKeys,
                            // The household's current name wins over the one stored when the
                            // item was added, so a rename shows up on old lines too.
                            customItems = customItems.map { item ->
                                val memberName = members.firstOrNull { it.userId == item.addedByUserId }?.displayName
                                if (memberName != null) item.copy(addedByName = memberName) else item
                            },
                        ),
                        ownerDisplayName = if (plan.householdId != null) {
                            members.firstOrNull { it.userId == plan.userId }?.displayName
                        } else {
                            null
                        },
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ShoppingListUiState.Loading,
        )

    fun onDraftChanged(text: String) {
        _draft.value = text
    }

    /** Adds the draft as a hand-added line, credited to the signed-in user. A blank draft is ignored. */
    fun onAddDraftItem() {
        val name = _draft.value.trim()
        if (name.isEmpty()) return
        // Cleared up front so a double-tap on "add" can't add the same line twice.
        _draft.value = ""
        val (userId, userName) = when (val session = sessionManager.userSession.value) {
            is UserSession.Authenticated -> session.user.uuid to session.user.displayName.ifBlank { session.user.email }
            is UserSession.Anonymous -> session.localUserId to null
            is UserSession.Loading -> null to null
        }
        viewModelScope.launch {
            try {
                shoppingListRepository.addCustomItem(mealPlanId, name, userId, userName)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onAddDraftItem: failed for plan $mealPlanId")
                _draft.value = name
                _events.emit(ShoppingListEvent.ShowError("Couldn't add this item"))
            }
        }
    }

    fun onToggleItem(item: ShoppingListItem) {
        viewModelScope.launch {
            try {
                val customId = item.customItemId
                if (customId != null) {
                    shoppingListRepository.setCustomItemChecked(customId, checked = !item.isChecked)
                } else {
                    shoppingListRepository.setChecked(mealPlanId, item.key, checked = !item.isChecked)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onToggleItem: failed for item ${item.key}")
                _events.emit(ShoppingListEvent.ShowError("Couldn't update this item"))
            }
        }
    }

    /** Blank or unchanged names are ignored; the item's tick is unaffected either way. */
    fun onRenameItem(item: ShoppingListItem, newName: String) {
        val name = newName.trim()
        if (name.isEmpty() || name == item.displayName) return
        viewModelScope.launch {
            try {
                val customId = item.customItemId
                if (customId != null) {
                    shoppingListRepository.renameCustomItem(customId, name)
                } else {
                    shoppingListRepository.renameItem(mealPlanId, item.key, name)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onRenameItem: failed for item ${item.key}")
                _events.emit(ShoppingListEvent.ShowError("Couldn't rename this item"))
            }
        }
    }

    fun onDeleteItem(item: ShoppingListItem) {
        viewModelScope.launch {
            try {
                setRemoved(item.key, removed = true)
                _events.emit(ShoppingListEvent.ItemRemoved(item.key, item.displayName))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onDeleteItem: failed for item ${item.key}")
                _events.emit(ShoppingListEvent.ShowError("Couldn't remove this item"))
            }
        }
    }

    fun onUndoRemove(itemKey: String) {
        viewModelScope.launch {
            try {
                setRemoved(itemKey, removed = false)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onUndoRemove: failed for item $itemKey")
                _events.emit(ShoppingListEvent.ShowError("Couldn't restore this item"))
            }
        }
    }

    /** Routes by key: a hand-added line has its own row; a recipe-derived one is an overlay. */
    private suspend fun setRemoved(itemKey: String, removed: Boolean) {
        val customId = ShoppingListBuilder.customItemId(itemKey)
        if (customId != null) {
            shoppingListRepository.setCustomItemRemoved(customId, removed)
        } else {
            shoppingListRepository.setRemoved(mealPlanId, itemKey, removed)
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
