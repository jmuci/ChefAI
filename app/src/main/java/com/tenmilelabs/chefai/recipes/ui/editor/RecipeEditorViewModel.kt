package com.tenmilelabs.chefai.recipes.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDraftDao
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.core.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipe
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraft
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraftEntity
import com.tenmilelabs.chefai.recipes.domain.model.EditorMode
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import com.tenmilelabs.chefai.recipes.ui.create.IngredientsFields
import com.tenmilelabs.chefai.recipes.ui.create.LabelsFields
import com.tenmilelabs.chefai.recipes.ui.create.RecipeFields
import com.tenmilelabs.chefai.recipes.ui.create.StepsFields
import com.tenmilelabs.chefai.recipes.ui.create.TagsFields
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    private val recipesRepository: RecipesRepository,
    private val metadataRepository: MetadataRepository,
    private val sessionManager: SessionManager,
    private val recipeDraftDao: RecipeDraftDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recipeIdArg: String? = savedStateHandle["recipeId"]
    val mode: EditorMode = if (recipeIdArg != null) {
        EditorMode.Edit(UUID.fromString(recipeIdArg))
    } else {
        EditorMode.Create
    }

    private val _state = MutableStateFlow(
        RecipeEditorState(
            mode = mode,
            recipeId = (mode as? EditorMode.Edit)?.recipeId ?: UUID.randomUUID(),
            isLoading = mode is EditorMode.Edit,
        )
    )
    val state: StateFlow<RecipeEditorState> = _state.asStateFlow()

    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects: Flow<EditorEffect> = _effects.receiveAsFlow()

    // Metadata streams for autocomplete
    private val allIngredients = metadataRepository.observeAllIngredients()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allTags = metadataRepository.observeAllTags()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allLabels = metadataRepository.observeAllLabels()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var autoSaveJob: Job? = null

    init {
        if (mode is EditorMode.Edit) {
            loadRecipe(mode.recipeId)
        } else {
            // In create mode, check for an existing draft to restore
            restoreDraftIfExists(_state.value.recipeId)
        }
        startAutoSave()
    }

    /**
     * Single entry point for all user actions.
     * Applies the pure reducer for synchronous state changes,
     * then dispatches side effects as needed.
     */
    fun dispatch(action: EditorAction) {
        _state.update { RecipeEditorReducer.reduce(it, action) }

        when (action) {
            is EditorAction.Save -> save()
            is EditorAction.ConfirmDelete -> delete()
            is EditorAction.IngredientInputChanged -> updateIngredientSuggestions(action.input)
            is EditorAction.TagInputChanged -> updateTagSuggestions(action.input)
            is EditorAction.LabelInputChanged -> updateLabelSuggestions(action.input)
            else -> { /* pure state change already handled by reducer */ }
        }
    }

    // --- Load & Draft Restore ---

    private fun loadRecipe(recipeId: UUID) {
        viewModelScope.launch {
            try {
                // Check for an existing draft first (user had unsaved changes)
                val existingDraft = withContext(ioDispatcher) {
                    recipeDraftDao.getDraft(recipeId)
                }

                if (existingDraft != null) {
                    val draft = existingDraft.toRecipeDraft()
                    populateFromDraft(draft)
                    // Still need the original recipe for dirty tracking
                    loadOriginalForDirtyTracking(recipeId)
                } else {
                    // Load fresh from repository
                    val recipe = withContext(ioDispatcher) {
                        recipesRepository.getRecipe(recipeId)
                    }
                    if (recipe != null) {
                        val draft = recipe.toRecipeDraft()
                        populateFromDraft(draft)
                        _state.update { it.copy(originalDraft = draft, isDirty = false) }
                    } else {
                        _effects.send(EditorEffect.ShowError("Recipe not found"))
                        _effects.send(EditorEffect.NavigateBack)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load recipe")
                _effects.send(EditorEffect.ShowError("Failed to load recipe"))
                _effects.send(EditorEffect.NavigateBack)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadOriginalForDirtyTracking(recipeId: UUID) {
        val recipe = withContext(ioDispatcher) {
            recipesRepository.getRecipe(recipeId)
        }
        if (recipe != null) {
            val originalDraft = recipe.toRecipeDraft()
            _state.update { it.copy(originalDraft = originalDraft) }
        }
    }

    private fun restoreDraftIfExists(recipeId: UUID) {
        viewModelScope.launch {
            val existingDraft = withContext(ioDispatcher) {
                recipeDraftDao.getDraft(recipeId)
            }
            if (existingDraft != null) {
                val draft = existingDraft.toRecipeDraft()
                populateFromDraft(draft)
            }
        }
    }

    private fun populateFromDraft(draft: RecipeDraft) {
        _state.update {
            it.copy(
                recipeFields = RecipeFields(
                    title = draft.title,
                    description = draft.description,
                    imageUrl = draft.imageUrl,
                    selectedImageUri = draft.selectedImageUri,
                    prepTimeMinutes = draft.prepTimeMinutes,
                    cookTimeMinutes = draft.cookTimeMinutes,
                    servings = draft.servings,
                    externalUrl = draft.externalUrl,
                ),
                ingredients = IngredientsFields(
                    selectedIngredients = draft.ingredients,
                ),
                steps = StepsFields(
                    steps = draft.steps,
                ),
                tags = TagsFields(
                    selectedTags = draft.tags,
                ),
                labels = LabelsFields(
                    selectedLabels = draft.labels,
                ),
                version = draft.version,
            )
        }
    }

    // --- Autocomplete Suggestions ---

    private fun updateIngredientSuggestions(input: String) {
        _state.update {
            it.copy(
                ingredients = it.ingredients.copy(
                    suggestions = if (input.isNotBlank()) {
                        allIngredients.value
                            .filter { ingredient ->
                                ingredient.displayName.contains(input, ignoreCase = true)
                            }
                            .map { ingredient -> ingredient.displayName }
                            .take(5)
                    } else {
                        emptyList()
                    }
                )
            )
        }
    }

    private fun updateTagSuggestions(input: String) {
        _state.update {
            val selectedNames = it.tags.selectedTags.map { tag -> tag.displayName }.toSet()
            it.copy(
                tags = it.tags.copy(
                    suggestions = if (input.isNotBlank()) {
                        allTags.value
                            .filter { tag ->
                                tag.displayName.contains(input, ignoreCase = true) &&
                                    tag.displayName !in selectedNames
                            }
                            .map { tag -> tag.displayName }
                            .take(5)
                    } else {
                        emptyList()
                    }
                )
            )
        }
    }

    private fun updateLabelSuggestions(input: String) {
        _state.update {
            val selectedNames = it.labels.selectedLabels.map { label -> label.displayName }.toSet()
            it.copy(
                labels = it.labels.copy(
                    suggestions = if (input.isNotBlank()) {
                        allLabels.value
                            .filter { label ->
                                label.displayName.contains(input, ignoreCase = true) &&
                                    label.displayName !in selectedNames
                            }
                            .map { label -> label.displayName }
                            .take(5)
                    } else {
                        emptyList()
                    }
                )
            )
        }
    }

    // --- Save ---

    private fun save() {
        if (!_state.value.isFormValid || _state.value.isSaving) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }

            try {
                val userId = sessionManager.getCurrentUserId()
                if (userId == null) {
                    _state.update { it.copy(isSaving = false, saveError = "No active session") }
                    return@launch
                }

                val currentUser = sessionManager.getCurrentUser() ?: User(
                    uuid = userId,
                    displayName = "Guest",
                    email = "",
                    avatarUrl = "",
                )

                val currentState = _state.value
                val draft = currentState.toRecipeDraft()
                val recipe = draft.toRecipe(currentUser)

                withContext(ioDispatcher) {
                    when (mode) {
                        is EditorMode.Create -> recipesRepository.createRecipe(recipe)
                        is EditorMode.Edit -> recipesRepository.updateRecipe(recipe)
                    }
                    // Clear draft on success
                    recipeDraftDao.deleteDraft(currentState.recipeId)
                }

                _state.update { it.copy(isSaving = false) }
                _effects.send(EditorEffect.RecipeSaved)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save recipe")
                _state.update {
                    it.copy(
                        isSaving = false,
                        saveError = e.message ?: "Failed to save recipe",
                    )
                }
            }
        }
    }

    // --- Delete ---

    private fun delete() {
        val editRecipeId = (mode as? EditorMode.Edit)?.recipeId ?: return

        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, showDeleteConfirmation = false) }
            try {
                withContext(ioDispatcher) {
                    recipesRepository.softDeleteRecipe(editRecipeId)
                    recipeDraftDao.deleteDraft(editRecipeId)
                }
                _state.update { it.copy(isDeleting = false) }
                _effects.send(EditorEffect.RecipeDeleted)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete recipe")
                _state.update {
                    it.copy(
                        isDeleting = false,
                        saveError = e.message ?: "Failed to delete recipe",
                    )
                }
            }
        }
    }

    // --- Auto-Save ---

    private fun startAutoSave() {
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_SAVE_INTERVAL_MS)
                if (_state.value.isDirty && !_state.value.isSaving) {
                    saveDraftToRoom()
                }
            }
        }
    }

    private suspend fun saveDraftToRoom() {
        try {
            withContext(ioDispatcher) {
                val draft = _state.value.toRecipeDraft()
                recipeDraftDao.saveDraft(draft.toRecipeDraftEntity())
            }
        } catch (e: Exception) {
            Timber.e(e, "Auto-save draft failed")
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
    }

    companion object {
        const val AUTO_SAVE_INTERVAL_MS = 10_000L
    }
}
