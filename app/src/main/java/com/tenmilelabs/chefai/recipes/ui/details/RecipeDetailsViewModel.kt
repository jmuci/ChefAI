package com.tenmilelabs.chefai.recipes.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.util.Async
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import com.tenmilelabs.chefai.recipes.domain.scaling.RecipeScaling
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

data class RecipesDetailsUiState(
    val recipe: Recipe? = null,
    val isLoading: Boolean = false,
    val isBookmarked: Boolean = false,
    val userMessage: Int? = null,
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    /** True when this recipe was opened from a meal plan slot, so a cooked toggle applies. */
    val showCookedToggle: Boolean = false,
    val isCooked: Boolean = false,
    val servings: ServingsUiState = ServingsUiState.DEFAULT,
    /** The units the ingredient list is read in — a display choice, never written to the recipe. */
    val measurementSystem: MeasurementSystem = MeasurementSystem.DEFAULT,
)

/** What the portions stepper renders: the chosen count and the counts it may be moved between. */
data class ServingsUiState(
    val current: Int,
    /**
     * The yield [current] is scaled against — the recipe's own, or [RecipeScaling.DEFAULT_SERVINGS]
     * when it published none. Held rather than re-derived so the stepper and the ingredient list
     * cannot end up dividing by different numbers.
     */
    val base: Int,
    val range: IntRange,
    /**
     * True when the recipe never published a yield, so [base] is [RecipeScaling.DEFAULT_SERVINGS]
     * rather than something the recipe actually said. Scaling is still correct in relative terms;
     * the absolute number is an assumption worth surfacing.
     */
    val isEstimated: Boolean,
) {
    companion object {
        /**
         * Placeholder for the states that have no recipe yet (loading, error). Not flagged
         * estimated: nothing was assumed about a recipe that hasn't arrived.
         */
        val DEFAULT = ServingsUiState(
            current = RecipeScaling.DEFAULT_SERVINGS,
            base = RecipeScaling.DEFAULT_SERVINGS,
            range = RecipeScaling.servingsRange(RecipeScaling.DEFAULT_SERVINGS),
            isEstimated = false,
        )

        /** The state a recipe opens at: its own yield, unscaled. */
        fun forRecipeServings(recipeServings: Int): ServingsUiState {
            val base = RecipeScaling.baseServings(recipeServings)
            return ServingsUiState(
                current = base,
                base = base,
                range = RecipeScaling.servingsRange(base),
                isEstimated = recipeServings < RecipeScaling.MIN_SERVINGS,
            )
        }
    }
}

/**
 * Everything the user can do on the recipe details screen. One action type rather than a callback
 * per control, so the screen's surface doesn't widen with every new interaction.
 */
sealed interface RecipeDetailsAction {
    data object EditClicked : RecipeDetailsAction
    data object ToggleBookmark : RecipeDetailsAction
    data object DeleteClicked : RecipeDetailsAction
    data object ConfirmDelete : RecipeDetailsAction
    data object DismissDeleteDialog : RecipeDetailsAction
    data object ToggleCooked : RecipeDetailsAction
    data class ServingsChanged(val servings: Int) : RecipeDetailsAction
}

/** One-shot side effects emitted by [RecipeDetailsViewModel], consumed by the UI. */
sealed interface RecipeDetailsEffect {
    data object RecipeDeleted : RecipeDetailsEffect
}

private data class DeleteUiState(
    val showConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
)

/**
 * Intermediate combine result — `combine` only has typed overloads up to 5 flows, so this bundles
 * the first 5 to leave room for the cooked-state flow to combine on top.
 */
private data class CombinedState(
    val isLoading: Boolean,
    val recipeAsync: Async<Recipe>,
    val userMessage: Int?,
    val isBookmarked: Boolean,
    val deleteUi: DeleteUiState,
)

/** The planned meal (day + slot) a recipe was opened from, kept as a pair so one is never set without the other. */
private data class MealPlanSlotRef(val dayId: UUID, val slot: MealSlot)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeDetailsViewModel @Inject constructor(
    private val recipesRepository: RecipesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val sessionManager: SessionManager,
    private val mealPlanRepository: MealPlanRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val recipeUuid: UUID = UUID.fromString(savedStateHandle[AppDestinationArgs.RECIPE_ID_ARG]!!)

    /**
     * Present only when this screen was opened from a meal plan slot
     * ([com.tenmilelabs.chefai.core.ui.navigation.AppDestinations.MEAL_PLAN_RECIPE_DETAIL]) — the
     * day/slot pair a "mark as cooked" toggle here would act on. `null` on the plain recipe
     * details route, which hides the toggle entirely.
     */
    private val mealPlanSlotRef: MealPlanSlotRef? = run {
        val dayId = savedStateHandle.get<String>(AppDestinationArgs.MEAL_PLAN_DAY_ID_ARG)
            ?.let(UUID::fromString)
        val slot = savedStateHandle.get<String>(AppDestinationArgs.MEAL_PLAN_SLOT_ARG)
            ?.let(MealSlot::valueOf)
        if (dayId != null && slot != null) MealPlanSlotRef(dayId, slot) else null
    }

    private val _isLoading = MutableStateFlow(false)
    private val _userMessage: MutableStateFlow<Int?> = MutableStateFlow(null)
    private val _deleteUi = MutableStateFlow(DeleteUiState())

    /**
     * Set right before the soft-delete write, ahead of the repository call completing. Once true,
     * a null emission from [_recipeAsync] is our own delete taking effect (Room's `deletedAt IS
     * NULL` filter dropping the row), not a load failure — the combine below reads this to avoid
     * flashing the "recipe not found" error while [RecipeDetailsEffect.RecipeDeleted] navigates
     * the screen away.
     */
    private val _isDeleted = MutableStateFlow(false)

    private val _effects = Channel<RecipeDetailsEffect>(Channel.BUFFERED)
    val effects: Flow<RecipeDetailsEffect> = _effects.receiveAsFlow()

    private val _recipeAsync: Flow<Async<Recipe>> = recipesRepository.getRecipeStream(recipeUuid)
        .map {
            if (it != null) {
                Async.Success(it)
            } else {
                Async.Error(R.string.loading_recipe_details_error)
            }
        }
        .catch { emit(Async.Error(R.string.loading_recipe_details_error)) }

    private val _isBookmarked: Flow<Boolean> = sessionManager.userSession
        .flatMapLatest { session ->
            val userId = when (session) {
                is UserSession.Loading -> return@flatMapLatest flowOf(false)
                is UserSession.Anonymous -> session.localUserId
                is UserSession.Authenticated -> session.user.uuid
            }
            collectionsRepository.observeBookmarkedRecipeIds(userId).map { ids -> recipeUuid in ids }
        }

    private val _isCooked: Flow<Boolean> = mealPlanSlotRef?.let { ref ->
        mealPlanRepository.observeMealPlanDay(ref.dayId).map { day -> day?.cookedAtFor(ref.slot) != null }
    } ?: flowOf(false)

    /**
     * The user's chosen portion count, or [SERVINGS_UNSET] while they haven't chosen one.
     *
     * Deliberately holds *only* the override rather than the effective value: the recipe stream
     * re-emits on every unrelated write (a bookmark, a sync pull), and folding the recipe's own
     * yield in here would reset the user's choice each time. It is backed by [SavedStateHandle] so
     * the choice survives process death, and cleared implicitly by leaving the screen — scaling is
     * a way of reading a recipe, not an edit to it.
     */
    private val _selectedServings: StateFlow<Int> =
        savedStateHandle.getStateFlow(SELECTED_SERVINGS_KEY, SERVINGS_UNSET)

    private val _measurementSystem: Flow<MeasurementSystem> =
        userPreferencesRepository.measurementSystem

    private val _combinedState = combine(
        _isLoading, _recipeAsync, _userMessage, _isBookmarked, _deleteUi
    ) { isLoading, recipeAsync, userMessage, isBookmarked, deleteUi ->
        CombinedState(isLoading, recipeAsync, userMessage, isBookmarked, deleteUi)
    }

    val uiState: StateFlow<RecipesDetailsUiState> = combine(
        _combinedState, _isCooked, _selectedServings, _measurementSystem
    ) { combined, isCooked, selectedServings, measurementSystem ->
        when (val recipeAsync = combined.recipeAsync) {
            Async.Loading -> {
                RecipesDetailsUiState(isLoading = true)
            }

            is Async.Error -> {
                if (_isDeleted.value) {
                    RecipesDetailsUiState(isLoading = true, isDeleting = combined.deleteUi.isDeleting)
                } else {
                    RecipesDetailsUiState(userMessage = recipeAsync.errorMessage)
                }
            }

            is Async.Success -> {
                val recipe = recipeAsync.data
                val base = ServingsUiState.forRecipeServings(recipe.servings)
                // An out-of-range override (SERVINGS_UNSET, or a value saved for a recipe whose
                // yield has since been edited) falls back to the recipe's own yield.
                val servings = if (selectedServings in base.range) {
                    base.copy(current = selectedServings)
                } else {
                    base
                }
                RecipesDetailsUiState(
                    recipe = recipe,
                    isLoading = combined.isLoading,
                    isBookmarked = combined.isBookmarked,
                    userMessage = combined.userMessage,
                    showDeleteConfirmation = combined.deleteUi.showConfirmation,
                    isDeleting = combined.deleteUi.isDeleting,
                    showCookedToggle = mealPlanSlotRef != null,
                    isCooked = isCooked,
                    servings = servings,
                    measurementSystem = measurementSystem,
                )
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = RecipesDetailsUiState(isLoading = true)
        )

    fun snackbarMessageShown() {
        _userMessage.value = null
    }

    /**
     * Rescales the ingredient list to [servings].
     *
     * Stored raw. Clamping here would have to read the range out of [uiState], which is the
     * placeholder `1..10` until the recipe loads — that would silently cap a batch recipe's own
     * yield at 10. The combine above validates the stored value against the recipe that actually
     * arrived and falls back to its yield when it doesn't fit.
     */
    fun onServingsChange(servings: Int) {
        savedStateHandle[SELECTED_SERVINGS_KEY] = servings
    }

    fun toggleBookmark() {
        val userId = sessionManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            if (uiState.value.isBookmarked) {
                collectionsRepository.removeBookmark(userId, recipeUuid)
            } else {
                collectionsRepository.addBookmark(userId, recipeUuid)
            }
        }
    }

    /** Flips the meal plan slot this recipe was opened from between cooked and outstanding. */
    fun onToggleCooked() {
        val ref = mealPlanSlotRef ?: return
        viewModelScope.launch {
            try {
                mealPlanRepository.setMealCooked(ref.dayId, ref.slot, cooked = !uiState.value.isCooked)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "onToggleCooked: failed for day ${ref.dayId} slot ${ref.slot}")
                _userMessage.value = R.string.meal_plan_toggle_cooked_error
            }
        }
    }

    fun onDeleteClick() {
        _deleteUi.value = _deleteUi.value.copy(showConfirmation = true)
    }

    fun dismissDeleteDialog() {
        _deleteUi.value = _deleteUi.value.copy(showConfirmation = false)
    }

    fun confirmDelete() {
        _isDeleted.value = true
        _deleteUi.value = DeleteUiState(showConfirmation = false, isDeleting = true)
        viewModelScope.launch {
            try {
                recipesRepository.softDeleteRecipe(recipeUuid)
                _effects.send(RecipeDetailsEffect.RecipeDeleted)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete recipe")
                _deleteUi.value = DeleteUiState(isDeleting = false)
                _userMessage.value = R.string.delete_recipe_error
            }
        }
    }

    private companion object {
        const val SELECTED_SERVINGS_KEY = "recipeDetailsSelectedServings"

        /** No choice made yet. Never a valid portion count, so it can't collide with one. */
        const val SERVINGS_UNSET = 0
    }
}
