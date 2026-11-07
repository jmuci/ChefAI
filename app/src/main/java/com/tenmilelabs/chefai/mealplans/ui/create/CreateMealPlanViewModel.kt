package com.tenmilelabs.chefai.mealplans.ui.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import com.tenmilelabs.chefai.mealplans.data.local.SavedPlanImageStore
import com.tenmilelabs.chefai.mealplans.domain.repository.SavedMealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.usecase.GenerateMealPlanUseCase
import com.tenmilelabs.chefai.mealplans.domain.week.DAYS_IN_WEEK
import com.tenmilelabs.chefai.mealplans.domain.week.MealPlanOverlap
import com.tenmilelabs.chefai.mealplans.domain.week.defaultMealPlanStartDate
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

@HiltViewModel
class CreateMealPlanViewModel @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val sessionManager: SessionManager,
    private val recipesRepository: RecipesRepository,
    private val generateMealPlanUseCase: GenerateMealPlanUseCase,
    private val savedMealPlanRepository: SavedMealPlanRepository,
    private val savedPlanImageStore: SavedPlanImageStore,
    savedStateHandle: SavedStateHandle,
    private val clock: Clock,
) : ViewModel() {

    /** Recipe the wizard was opened from ("Add to Meal Plan" → create), slotted into the new plan. */
    private val includedRecipeId: UUID? = savedStateHandle.get<String>(AppDestinationArgs.RECIPE_ID_ARG)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    /**
     * First day of the week the week view was showing when its "+" opened the wizard; `null` from
     * any other entry point. Narrows the default start date to that week.
     */
    private val browsedWeekStart: LocalDate? = savedStateHandle.get<String>(AppDestinationArgs.WEEK_START_ARG)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Start date the caller asked for ("+ Add meal" on a day); replaces the computed default. */
    private val requestedStartDate: LocalDate? = savedStateHandle.get<String>(AppDestinationArgs.START_DATE_ARG)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Which meal of the first day [includedRecipeId] fills. */
    private val includedSlot: MealSlot = savedStateHandle.get<String>(AppDestinationArgs.MEAL_PLAN_SLOT_ARG)
        ?.let { name -> MealSlot.entries.firstOrNull { it.name == name } }
        ?: MealSlot.DINNER

    /**
     * Key of the saved plan this wizard run may create. Generated up front so the photo can be
     * copied under its final name the moment it is picked.
     */
    internal val savedPlanId: UUID = UuidV7Generator.newId()

    private val _uiState = MutableStateFlow(
        CreateMealPlanUiState(
            startDate = requestedStartDate,
            // A lunch the user already chose only fits a plan that has lunches.
            mealType = if (includedRecipeId != null && includedSlot == MealSlot.LUNCH) {
                MealType.DINNER_AND_LUNCH
            } else {
                MealType.DINNER
            },
        )
    )
    val uiState: StateFlow<CreateMealPlanUiState> = _uiState.asStateFlow()

    // Buffered Channel: generation takes seconds, and an event emitted into a rendezvous
    // SharedFlow while the collector is being re-created (rotation) was dropped — leaving the user
    // on the wizard with the plan already saved, one tap away from a duplicate.
    private val _uiEvent = Channel<CreateMealPlanEvent>(Channel.BUFFERED)
    val uiEvents: Flow<CreateMealPlanEvent> = _uiEvent.receiveAsFlow()

    init {
        loadRecipeCount()
        loadDefaultStartDate()
    }

    private fun loadDefaultStartDate() {
        val today = LocalDate.now(clock)
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            _uiState.update { it.copy(startDate = it.startDate ?: today) }
            return
        }
        viewModelScope.launch {
            val plans = try {
                mealPlanRepository.observeMealPlansForUser(userId).first()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "Could not read existing meal plans for the default start date")
                emptyList()
            }
            val default = defaultMealPlanStartDate(
                plans = plans,
                today = today,
                zone = clock.zone,
                week = browsedWeekStart?.let { it..it.plusDays(DAYS_IN_WEEK - 1L) },
            )
            // A date the user already picked wins over a default that arrived after it.
            _uiState.update { it.copy(startDate = it.startDate ?: default) }
        }
    }

    private fun loadRecipeCount() {
        val userId = sessionManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            val count = recipesRepository.getRecipeCountForUser(userId)
            val tooSmall = count < MIN_COLLECTION_RECIPES
            _uiState.update {
                it.copy(
                    collectionTooSmall = tooSmall,
                    // Auto-switch to INCLUDE_PUBLIC when collection is too small
                    recipeSource = if (tooSmall) RecipeSource.INCLUDE_PUBLIC else it.recipeSource,
                )
            }
        }
    }

    fun onAction(action: WizardAction) {
        when (action) {
            is WizardAction.SetStartDate -> _uiState.update { it.copy(startDate = action.date) }
            is WizardAction.SetPlanLength -> _uiState.update { it.copy(planLengthDays = action.days) }
            is WizardAction.SetMealType -> _uiState.update { it.copy(mealType = action.type) }
            is WizardAction.SetServings -> _uiState.update { it.copy(servingsPerMeal = action.count.coerceIn(1, 12)) }
            is WizardAction.ToggleDietaryRestriction -> toggleDietaryRestriction(action.restriction)
            is WizardAction.SetRecipeSource -> {
                // Guard: don't allow COLLECTION_ONLY when collection is too small
                if (action.source == RecipeSource.COLLECTION_ONLY && _uiState.value.collectionTooSmall) return
                _uiState.update { it.copy(recipeSource = action.source) }
            }
            is WizardAction.SetMaxPrepTime -> _uiState.update { it.copy(maxPrepTimeMinutes = action.minutes) }
            is WizardAction.SetBatchCooking -> _uiState.update { it.copy(batchCooking = action.enabled) }
            is WizardAction.SetLeftoverFriendly -> _uiState.update { it.copy(leftoverFriendly = action.enabled) }
            is WizardAction.SetVarietyPreference -> _uiState.update { it.copy(varietyPreference = action.preference) }
            is WizardAction.SetSaveForLater -> _uiState.update { it.copy(saveForLater = action.enabled) }
            is WizardAction.SetSavedTitle -> _uiState.update {
                it.copy(savedTitle = action.title.take(SavedMealPlan.MAX_TITLE_LENGTH))
            }
            is WizardAction.SavedImagePicked -> action.uri?.let(::importSavedImage)
            is WizardAction.RemoveSavedImage -> removeSavedImage()
            is WizardAction.RequestSavedImagePick -> Unit // handled by the screen, which owns the launcher
            is WizardAction.SaveMealPlan -> saveMealPlan()
        }
    }

    private fun toggleDietaryRestriction(restriction: DietaryRestriction) {
        _uiState.update { state ->
            val current = state.dietaryRestrictions
            val updated = if (restriction == DietaryRestriction.NONE) {
                // Selecting "None" clears all others
                if (DietaryRestriction.NONE in current) emptySet() else setOf(DietaryRestriction.NONE)
            } else {
                // Selecting a specific restriction removes NONE
                val withoutNone = current - DietaryRestriction.NONE
                if (restriction in withoutNone) withoutNone - restriction else withoutNone + restriction
            }
            state.copy(dietaryRestrictions = updated)
        }
    }

    private fun importSavedImage(uri: String) {
        _uiState.update { it.copy(isImportingImage = true) }
        viewModelScope.launch {
            val path = try {
                savedPlanImageStore.writeFromUri(savedPlanId, uri)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "Could not copy the saved-plan photo")
                null
            }
            // A failed copy keeps whatever photo was there before (the store writes atomically).
            _uiState.update { it.copy(isImportingImage = false, savedImagePath = path ?: it.savedImagePath) }
            if (path == null) _uiEvent.send(CreateMealPlanEvent.ShowError(R.string.wizard_saved_photo_failed))
        }
    }

    private fun removeSavedImage() {
        _uiState.update { it.copy(savedImagePath = null) }
        viewModelScope.launch { deleteSavedImageFile() }
    }

    private suspend fun deleteSavedImageFile() {
        try {
            savedPlanImageStore.delete(savedPlanId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "Could not delete the saved-plan photo")
        }
    }

    private fun saveMealPlan() {
        val userId = sessionManager.getCurrentUserId() ?: return
        val state = _uiState.value
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true) }

        val savingForLater = state.saveForLater && state.savedTitle.isNotBlank()
        val savedTitle = state.savedTitle.trim()

        val preferences = MealPlanPreferences(
            planLengthDays = state.planLengthDays,
            mealType = state.mealType,
            dietaryRestrictions = state.dietaryRestrictions,
            recipeSource = state.recipeSource,
            maxPrepTimeMinutes = state.maxPrepTimeMinutes,
            servingsPerMeal = state.servingsPerMeal,
            batchCooking = state.batchCooking,
            leftoverFriendly = state.leftoverFriendly,
            varietyPreference = state.varietyPreference,
        )

        viewModelScope.launch {
            val now = clock.millis()
            val startDate = state.startDate ?: LocalDate.now(clock)
            // Read before the new plan exists, so it cannot overlap itself.
            val overlappingPlanName = findOverlappingPlanName(userId, startDate, state.planLengthDays)
            val mealPlanId: UUID
            try {
                mealPlanId = UuidV7Generator.newId()
                val mealPlan = MealPlan(
                    uuid = mealPlanId,
                    userId = userId,
                    name = if (savingForLater) savedTitle else "${state.planLengthDays}-day meal plan",
                    preferences = preferences,
                    status = MealPlanStatus.DRAFT,
                    createdAt = now,
                    updatedAt = now,
                    days = emptyList(),
                    startDate = startDate,
                )
                mealPlanRepository.createMealPlan(mealPlan)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isSaving = false) }
                _uiEvent.send(CreateMealPlanEvent.ShowError(R.string.meal_plan_save_error))
                return@launch
            }

            // Plan saved locally — now attempt immediate generation, then DRAFT so the user can
            // retry from the detail screen. Which generation path runs depends on session type and
            // recipeSource: see GenerateMealPlanUseCase's doc.
            // The plan is already saved; a failure here (e.g. the local fallback's Room reads)
            // just leaves it as a draft the detail screen can retry, rather than crashing.
            val filled = try {
                generateMealPlanUseCase(mealPlanId, preferences)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Meal plan generation failed for $mealPlanId")
                false
            }

            // Assigned after generation, which replaces the plan's days wholesale and knows nothing of this
            // recipe, so it may have dealt it elsewhere too — hence no repeats. A dinners-only
            // plan has no lunch slot, so a lunch pick moves to dinner if the user changed the type.
            val slot = if (state.mealType == MealType.DINNER) MealSlot.DINNER else includedSlot
            val includedRecipeAssigned = includedRecipeId?.let { recipeId ->
                try {
                    mealPlanRepository.assignRecipeToSlotWithoutRepeats(mealPlanId, 0, slot, recipeId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "Failed to include recipe $recipeId in plan $mealPlanId")
                    false
                }
            } ?: false

            if (savingForLater) {
                // Snapshot of the plan as generated; later edits to the dated plan don't reach it.
                val saved = try {
                    savedMealPlanRepository.saveFromMealPlan(
                        savedPlanId = savedPlanId,
                        userId = userId,
                        mealPlanId = mealPlanId,
                        title = savedTitle,
                        heroImagePath = state.savedImagePath,
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Result.failure(e)
                }
                saved.onFailure { e ->
                    // The dated plan stands; only the template is missing (e.g. a DRAFT with no days).
                    Timber.w(e, "Could not save meal plan $mealPlanId as a saved plan")
                    deleteSavedImageFile()
                    _uiEvent.send(CreateMealPlanEvent.ShowError(R.string.saved_plan_save_failed))
                }
            } else if (state.savedImagePath != null) {
                deleteSavedImageFile() // photo picked, then the toggle was switched off
            }

            _uiState.update { it.copy(isSaving = false) }
            _uiEvent.send(
                if (filled || includedRecipeAssigned) {
                    CreateMealPlanEvent.MealPlanReady(mealPlanId, overlappingPlanName)
                } else {
                    CreateMealPlanEvent.MealPlanSavedAsDraft(mealPlanId, overlappingPlanName)
                }
            )
        }
    }

    /**
     * The name of an existing plan whose dates a new [lengthDays]-day plan starting on [start]
     * would share, or `null`. Only feeds a notice — overlap is allowed — so a failed read is logged
     * and treated as "no overlap" rather than blocking the save.
     */
    private suspend fun findOverlappingPlanName(userId: UUID, start: LocalDate, lengthDays: Int): String? =
        try {
            val existing = mealPlanRepository.observeMealPlansForUser(userId).first()
            MealPlanOverlap.overlapping(
                plans = existing,
                start = start,
                lengthDays = lengthDays,
                zone = clock.zone,
            ).minByOrNull { it.createdAt }?.name
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "Could not check for overlapping meal plans")
            null
        }

    companion object {
        internal const val MIN_COLLECTION_RECIPES = 20
    }
}
