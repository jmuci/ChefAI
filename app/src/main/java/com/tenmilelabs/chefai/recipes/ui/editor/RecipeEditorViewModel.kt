package com.tenmilelabs.chefai.recipes.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDraftDao
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.core.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipe
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraft
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraftEntity
import com.tenmilelabs.chefai.recipes.domain.model.EditorMode
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import com.tenmilelabs.chefai.recipes.domain.usecase.CachePickedImage
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
    private val collectionsRepository: CollectionsRepository,
    private val metadataRepository: MetadataRepository,
    private val sessionManager: SessionManager,
    private val recipeDraftDao: RecipeDraftDao,
    private val cachePickedImage: CachePickedImage,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recipeIdArg: String? = savedStateHandle[AppDestinationArgs.RECIPE_ID_ARG]

    /**
     * Id of a draft seeded by another screen (recipe URL import) for this editor to adopt.
     * Stays in **create** mode: [restoreDraftIfExists] picks the draft up on init, and saving takes
     * the create path rather than updating an existing recipe.
     */
    private val draftIdArg: String? = savedStateHandle[AppDestinationArgs.DRAFT_ID_ARG]

    val mode: EditorMode = recipeIdArg?.toUuidOrNull()
        ?.let { EditorMode.Edit(it) }
        ?: EditorMode.Create

    private val _state = MutableStateFlow(
        RecipeEditorState(
            mode = mode,
            recipeId = (mode as? EditorMode.Edit)?.recipeId
                ?: draftIdArg?.toUuidOrNull()
                ?: UuidV7Generator.newId(),
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
            is EditorAction.ImageSelected -> storePickedImage(action.uri)
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
            RecipeEditorReducer.recomputeValidity(
                it.copy(
                    recipeFields = RecipeFields(
                        title = draft.title,
                        description = draft.description,
                        imageUrl = draft.imageUrl,
                        localImagePath = draft.localImagePath,
                        prepTimeMinutes = draft.prepTimeMinutes,
                        cookTimeMinutes = draft.cookTimeMinutes,
                        servings = draft.servings,
                        caloriesPerServing = draft.caloriesPerServing,
                        proteinGramsPerServing = draft.proteinGramsPerServing,
                        externalUrl = draft.externalUrl,
                        privacy = draft.privacy,
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
            )
        }
    }

    // --- UI Convenience Methods ---
    // These resolve domain objects from string names before dispatching actions.
    // The Reducer only works with fully-formed domain types.

    fun selectIngredient(name: String) {
        val existing = allIngredients.value.find {
            it.displayName.equals(name, ignoreCase = true)
        }
        val ingredientId = existing?.uuid ?: UuidV7Generator.newId()
        dispatch(EditorAction.IngredientSelected(name, ingredientId))
    }

    fun addTagByName(name: String) {
        if (name.isBlank()) return
        val existing = allTags.value.find {
            it.displayName.equals(name, ignoreCase = true)
        }
        val tag = existing ?: Tag(uuid = UuidV7Generator.newId(), displayName = name)
        dispatch(EditorAction.AddTag(tag))
    }

    fun addLabelByName(name: String) {
        if (name.isBlank()) return
        val existing = allLabels.value.find {
            it.displayName.equals(name, ignoreCase = true)
        }
        val label = existing ?: Label(uuid = UuidV7Generator.newId(), displayName = name)
        dispatch(EditorAction.AddLabel(label))
    }

    // --- Image ---

    /**
     * Takes ownership of a photo the user picked, then reports the stored path back through the
     * reducer.
     *
     * Copied now rather than at save time on purpose: the picker hands back a `content://` whose read
     * grant is scoped to the Activity, so a URI merely held in state is unreadable once the process
     * is restarted — which is exactly how a picked photo used to vanish. The draft's recipeId is
     * already stable here, so the bytes land at the path the saved recipe will read from.
     */
    private fun storePickedImage(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val storedPath = cachePickedImage(_state.value.recipeId, uri)
            if (storedPath == null) {
                Timber.w("Could not store picked image for %s", _state.value.recipeId)
            }
            dispatch(EditorAction.PickedImageStored(storedPath))
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
                        is EditorMode.Create -> {
                            recipesRepository.createRecipe(recipe)
                            collectionsRepository.addBookmark(userId, recipe.uuid)
                        }
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

/**
 * Parses a nav-argument string as a UUID, returning `null` rather than throwing on malformed input.
 * Nav args arrive as untrusted strings (deep links included), and a bad one should degrade to a
 * blank new recipe instead of crashing the ViewModel during construction.
 */
private fun String.toUuidOrNull(): UUID? = try {
    UUID.fromString(this)
} catch (e: IllegalArgumentException) {
    Timber.w(e, "Malformed UUID nav argument: %s", this)
    null
}
