package com.tenmilelabs.chefai.recipes.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.core.domain.model.Ingredient
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.core.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CreateRecipeViewModel @Inject constructor(
    private val recipesRepository: RecipesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val metadataRepository: MetadataRepository,
    private val sessionManager: SessionManager,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateRecipeUiState())
    val uiState: StateFlow<CreateRecipeUiState> = _uiState.asStateFlow()

    // Use Eagerly to start collecting immediately and keep the data available for autocomplete
    private val allIngredients: StateFlow<List<Ingredient>> =
        metadataRepository.observeAllIngredients()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allTags: StateFlow<List<Tag>> = metadataRepository.observeAllTags()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allLabels: StateFlow<List<Label>> = metadataRepository.observeAllLabels()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Title
    fun onTitleChange(title: String) {
        _uiState.update { it.copy( recipeFields = it.recipeFields.copy(title = title)) }
        validateForm()
    }

    // Description
    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(recipeFields = it.recipeFields.copy(description = description)) }
        validateForm()
    }

    // Image URL
    fun onImageUrlChange(url: String) {
        _uiState.update { it.copy(recipeFields = it.recipeFields.copy(imageUrl = url)) }
    }

    // Selected Image URI
    fun onImageSelected(uri: String?) {
        _uiState.update { it.copy(recipeFields = it.recipeFields.copy(selectedImageUri = uri)) }
    }

    fun clearSelectedImage() {
        _uiState.update { it.copy(recipeFields = it.recipeFields.copy(selectedImageUri = null)) }
    }

    // Prep Time
    fun onPrepTimeChange(time: String) {
        if (time.isEmpty() || time.toIntOrNull() != null) {
            _uiState.update { it.copy(recipeFields = it.recipeFields.copy(prepTimeMinutes = time)) }
            validateForm()
        }
    }

    // Cook Time
    fun onCookTimeChange(time: String) {
        if (time.isEmpty() || time.toIntOrNull() != null) {
            _uiState.update { it.copy(recipeFields = it.recipeFields.copy(cookTimeMinutes = time)) }
            validateForm()
        }
    }

    // Servings
    fun onServingsChange(servings: String) {
        if (servings.isEmpty() || servings.toIntOrNull() != null) {
            _uiState.update { it.copy(recipeFields = it.recipeFields.copy(servings = servings)) }
            validateForm()
        }
    }

    // External URL
    fun onExternalUrlChange(url: String) {
        _uiState.update { it.copy(recipeFields = it.recipeFields.copy(externalUrl = url)) }
    }

    // Ingredient Input
    fun onIngredientInputChange(input: String) {
        _uiState.update {
            it.copy(
                ingredients = it.ingredients.copy(
                    input = input,
                    suggestions = if (input.isNotBlank()) {
                        allIngredients.value.filter { ingredient ->
                            ingredient.displayName.contains(input, ignoreCase = true)
                        }.map { it.displayName }.take(5)
                    } else {
                        emptyList()
                    }
                )
            )
        }
    }

    fun onIngredientQuantityChange(quantity: String) {
        if (quantity.isEmpty() || quantity.toDoubleOrNull() != null) {
            _uiState.update { it.copy(ingredients = it.ingredients.copy(quantity = quantity)) }
        }
    }

    fun onIngredientUnitChange(unit: String) {
        _uiState.update { it.copy(ingredients = it.ingredients.copy(unit = unit)) }
    }

    fun onIngredientSelected(ingredientName: String) {
        val state = _uiState.value.ingredients
        if (state.quantity.isNotBlank() && state.quantity.toDoubleOrNull() != null) {
            val existingIngredient = allIngredients.value.find { it.displayName.equals(ingredientName, ignoreCase = true) }
            val ingredientId = existingIngredient?.uuid ?: UuidV7Generator.newId()

            val ingredient = RecipeIngredient(
                ingredientId = ingredientId,
                ingredientDisplayName = ingredientName,
                quantity = state.quantity.toDouble(),
                unit = state.unit.ifBlank { "unit" },
                allergenName = null,
                srcCategory = null,
                srcSubcategory = null
            )

            _uiState.update {
                it.copy(
                    ingredients = it.ingredients.copy(
                        selectedIngredients = it.ingredients.selectedIngredients + ingredient,
                        input = "",
                        quantity = "",
                        unit = "",
                        suggestions = emptyList()
                    )
                )
            }
            validateForm()
        }
    }

    fun removeIngredient(ingredient: RecipeIngredient) {
        _uiState.update {
            it.copy(ingredients = it.ingredients.copy(selectedIngredients = it.ingredients.selectedIngredients - ingredient))
        }
        validateForm()
    }

    // Step Input
    fun onStepInputChange(input: String) {
        _uiState.update { it.copy(steps = it.steps.copy(input = input)) }
    }

    fun addStep() {
        val stepText = _uiState.value.steps.input.trim()
        if (stepText.isNotBlank()) {
            val newStep = RecipeStep(
                uuid = UuidV7Generator.newId(),
                orderIndex = _uiState.value.steps.steps.size,
                instruction = stepText
            )
            _uiState.update {
                it.copy(
                    steps = it.steps.copy(
                        steps = it.steps.steps + newStep,
                        input = ""
                    )
                )
            }
            validateForm()
        }
    }

    fun removeStep(step: RecipeStep) {
        _uiState.update { state ->
            val updatedSteps = state.steps.steps.filter { it != step }
                .mapIndexed { index, s -> s.copy(orderIndex = index) }
            state.copy(steps = state.steps.copy(steps = updatedSteps))
        }
        validateForm()
    }

    fun moveStepUp(step: RecipeStep) {
        val currentIndex = _uiState.value.steps.steps.indexOf(step)
        if (currentIndex > 0) {
            val mutableSteps = _uiState.value.steps.steps.toMutableList()
            mutableSteps.removeAt(currentIndex)
            mutableSteps.add(currentIndex - 1, step)
            _uiState.update {
                it.copy(
                    steps = it.steps.copy(
                        steps = mutableSteps.mapIndexed { index, s ->
                            s.copy(orderIndex = index)
                        }
                    )
                )
            }
        }
    }

    fun moveStepDown(step: RecipeStep) {
        val currentIndex = _uiState.value.steps.steps.indexOf(step)
        if (currentIndex < _uiState.value.steps.steps.size - 1) {
            val mutableSteps = _uiState.value.steps.steps.toMutableList()
            mutableSteps.removeAt(currentIndex)
            mutableSteps.add(currentIndex + 1, step)
            _uiState.update {
                it.copy(
                    steps = it.steps.copy(
                        steps = mutableSteps.mapIndexed { index, s ->
                            s.copy(orderIndex = index)
                        }
                    )
                )
            }
        }
    }

    // Tag Input
    fun onTagInputChange(input: String) {
        _uiState.update {
            it.copy(
                tags = it.tags.copy(
                    input = input,
                    suggestions = if (input.isNotBlank()) {
                        val selectedTagNames = it.tags.selectedTags.map { it.displayName }.toSet()
                        Timber.d("Tag input changed to: $input. Alltags.size: ${allTags.value.size} Alltags: ${allTags.value}")
                        allTags.value.filter { tag ->
                            tag.displayName.contains(input, ignoreCase = true) && tag.displayName !in selectedTagNames
                        }.map { it.displayName }.take(5)
                    } else {
                        emptyList()
                    }
                )
            )
        }
    }

    fun addTag(tagName: String = _uiState.value.tags.input.trim()) {
        if (tagName.isNotBlank() && _uiState.value.tags.selectedTags.none { it.displayName == tagName }) {
            val existingTag = allTags.value.find { it.displayName.equals(tagName, ignoreCase = true) }
            val newTag = existingTag ?: Tag(
                uuid = UuidV7Generator.newId(),
                displayName = tagName
            )
            _uiState.update {
                it.copy(
                    tags = it.tags.copy(
                        selectedTags = it.tags.selectedTags + newTag,
                        input = "",
                        suggestions = emptyList()
                    )
                )
            }
        }
    }

    fun removeTag(tag: Tag) {
        _uiState.update {
            it.copy(tags = it.tags.copy(selectedTags = it.tags.selectedTags - tag))
        }
    }

    // Label Input
    fun onLabelInputChange(input: String) {
        _uiState.update {
            it.copy(
                labels = it.labels.copy(
                    input = input,
                    suggestions = if (input.isNotBlank()) {
                        val selectedLabelNames = it.labels.selectedLabels.map { it.displayName }.toSet()
                        allLabels.value.filter { label ->
                            label.displayName.contains(input, ignoreCase = true) && label.displayName !in selectedLabelNames
                        }.map { it.displayName }.take(5)
                    } else {
                        emptyList()
                    }
                )
            )
        }
    }

    fun addLabel(labelName: String = _uiState.value.labels.input.trim()) {
        if (labelName.isNotBlank() && _uiState.value.labels.selectedLabels.none { it.displayName == labelName }) {
            val existingLabel = allLabels.value.find { it.displayName.equals(labelName, ignoreCase = true) }
            val newLabel = existingLabel ?: Label(
                uuid = UuidV7Generator.newId(),
                displayName = labelName
            )
            _uiState.update {
                it.copy(
                    labels = it.labels.copy(
                        selectedLabels = it.labels.selectedLabels + newLabel,
                        input = "",
                        suggestions = emptyList()
                    )
                )
            }
        }
    }

    fun removeLabel(label: Label) {
        _uiState.update {
            it.copy(labels = it.labels.copy(selectedLabels = it.labels.selectedLabels - label))
        }
    }

    // Form Validation
    private fun validateForm() {
        val state = _uiState.value
        val isValid = state.recipeFields.title.isNotBlank() &&
                state.recipeFields.description.isNotBlank() &&
                state.recipeFields.prepTimeMinutes.isNotBlank() &&
                state.recipeFields.cookTimeMinutes.isNotBlank() &&
                state.recipeFields.servings.isNotBlank() &&
                state.ingredients.selectedIngredients.isNotEmpty() &&
                state.steps.steps.isNotEmpty()

        _uiState.update { it.copy(isFormValid = isValid) }
    }

    // Save Recipe
    fun saveRecipe(onSuccess: () -> Unit) {
        if (!_uiState.value.isFormValid) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }

            try {
                // Get current user ID (works for both anonymous and authenticated sessions)
                val userId = sessionManager.getCurrentUserId()
                if (userId == null) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveError = "No active session"
                        )
                    }
                    return@launch
                }

                // Use full User for authenticated, minimal User for anonymous
                val currentUser = sessionManager.getCurrentUser() ?: User(
                    uuid = userId,
                    displayName = "Guest",
                    email = "",
                    avatarUrl = ""
                )

                val state = _uiState.value
                val recipe = Recipe(
                    uuid = UuidV7Generator.newId(),
                    title = state.recipeFields.title,
                    description = state.recipeFields.description,
                    imageUrl = state.recipeFields.imageUrl,
                    imageUrlThumbnail = state.recipeFields.imageUrl,
                    prepTimeMinutes = state.recipeFields.prepTimeMinutes.toInt(),
                    cookTimeMinutes = state.recipeFields.cookTimeMinutes.toInt(),
                    servings = state.recipeFields.servings.toInt(),
                    creator = currentUser,
                    recipeExternalUrl = state.recipeFields.externalUrl.ifBlank { null },
                    ingredients = state.ingredients.selectedIngredients,
                    steps = state.steps.steps,
                    tags = state.tags.selectedTags,
                    labels = state.labels.selectedLabels,
                    updatedAt = System.currentTimeMillis()
                )

                withContext(dispatcher) {
                    recipesRepository.createRecipe(recipe)
                    collectionsRepository.addBookmark(userId, recipe.uuid)
                }

                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: Exception) {
                Timber.e("Failed to save recipe: ${e.message}")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = e.message ?: "Failed to save recipe"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(saveError = null) }
    }
}
