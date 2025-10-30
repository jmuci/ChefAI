package com.tenmilelabs.chefai.ui.createrecipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.domain.model.Label
import com.tenmilelabs.chefai.domain.model.Recipe
import com.tenmilelabs.chefai.domain.model.RecipeStep
import com.tenmilelabs.chefai.domain.model.Tag
import com.tenmilelabs.chefai.domain.model.User
import com.tenmilelabs.chefai.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * UiState for the Create Recipe screen
 */
data class CreateRecipeUiState(
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val selectedImageUri: String? = null, // Local image URI from picker
    val prepTimeMinutes: String = "",
    val cookTimeMinutes: String = "",
    val servings: String = "",
    val externalUrl: String = "",

    // Ingredients
    val ingredientInput: String = "",
    val ingredientQuantity: String = "",
    val ingredientUnit: String = "",
    val selectedIngredients: List<RecipeIngredient> = emptyList(),
    val ingredientSuggestions: List<String> = emptyList(),

    // Steps
    val stepInput: String = "",
    val steps: List<RecipeStep> = emptyList(),

    // Tags
    val tagInput: String = "",
    val selectedTags: List<Tag> = emptyList(),
    val tagSuggestions: List<String> = emptyList(),

    // Labels
    val labelInput: String = "",
    val selectedLabels: List<Label> = emptyList(),
    val labelSuggestions: List<String> = emptyList(),

    // Validation & Status
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isFormValid: Boolean = false
)

@HiltViewModel
class CreateRecipeViewModel @Inject constructor(
    private val recipesRepository: RecipesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateRecipeUiState())
    val uiState: StateFlow<CreateRecipeUiState> = _uiState.asStateFlow()

    // Mock data for autocomplete - in real app, this would come from repository
    private val allTags = listOf(
        "Quick", "Easy", "Healthy", "Vegetarian", "Vegan", "Gluten-Free",
        "Dairy-Free", "Low-Carb", "High-Protein", "Spicy", "Sweet", "Savory"
    )

    private val allLabels = listOf(
        "Breakfast", "Lunch", "Dinner", "Snack", "Dessert", "Appetizer",
        "Main Course", "Side Dish", "Soup", "Salad", "Beverage"
    )

    private val allIngredients = listOf(
        "Flour", "Sugar", "Salt", "Pepper", "Olive Oil", "Butter", "Eggs",
        "Milk", "Cheese", "Chicken", "Beef", "Pork", "Fish", "Tomato",
        "Onion", "Garlic", "Rice", "Pasta", "Bread", "Lettuce"
    )

    // Title
    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title) }
        validateForm()
    }

    // Description
    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(description = description) }
        validateForm()
    }

    // Image URL
    fun onImageUrlChange(url: String) {
        _uiState.update { it.copy(imageUrl = url) }
    }

    // Selected Image URI
    fun onImageSelected(uri: String?) {
        _uiState.update { it.copy(selectedImageUri = uri) }
    }

    fun clearSelectedImage() {
        _uiState.update { it.copy(selectedImageUri = null) }
    }

    // Prep Time
    fun onPrepTimeChange(time: String) {
        if (time.isEmpty() || time.toIntOrNull() != null) {
            _uiState.update { it.copy(prepTimeMinutes = time) }
            validateForm()
        }
    }

    // Cook Time
    fun onCookTimeChange(time: String) {
        if (time.isEmpty() || time.toIntOrNull() != null) {
            _uiState.update { it.copy(cookTimeMinutes = time) }
            validateForm()
        }
    }

    // Servings
    fun onServingsChange(servings: String) {
        if (servings.isEmpty() || servings.toIntOrNull() != null) {
            _uiState.update { it.copy(servings = servings) }
            validateForm()
        }
    }

    // External URL
    fun onExternalUrlChange(url: String) {
        _uiState.update { it.copy(externalUrl = url) }
    }

    // Ingredient Input
    fun onIngredientInputChange(input: String) {
        _uiState.update {
            it.copy(
                ingredientInput = input,
                ingredientSuggestions = if (input.isNotBlank()) {
                    allIngredients.filter { ingredient ->
                        ingredient.contains(input, ignoreCase = true)
                    }.take(5)
                } else {
                    emptyList()
                }
            )
        }
    }

    fun onIngredientQuantityChange(quantity: String) {
        if (quantity.isEmpty() || quantity.toDoubleOrNull() != null) {
            _uiState.update { it.copy(ingredientQuantity = quantity) }
        }
    }

    fun onIngredientUnitChange(unit: String) {
        _uiState.update { it.copy(ingredientUnit = unit) }
    }

    fun onIngredientSelected(ingredientName: String) {
        val state = _uiState.value
        if (state.ingredientQuantity.isNotBlank() && state.ingredientQuantity.toDoubleOrNull() != null) {
            val ingredient = RecipeIngredient(
                ingredientId = UUID.randomUUID(),
                ingredientDisplayName = ingredientName,
                quantity = state.ingredientQuantity.toDouble(),
                unit = state.ingredientUnit.ifBlank { "unit" },
                allergenName = null,
                srcCategory = null,
                srcSubcategory = null
            )

            _uiState.update {
                it.copy(
                    selectedIngredients = it.selectedIngredients + ingredient,
                    ingredientInput = "",
                    ingredientQuantity = "",
                    ingredientUnit = "",
                    ingredientSuggestions = emptyList()
                )
            }
            validateForm()
        }
    }

    fun removeIngredient(ingredient: RecipeIngredient) {
        _uiState.update {
            it.copy(selectedIngredients = it.selectedIngredients - ingredient)
        }
        validateForm()
    }

    // Step Input
    fun onStepInputChange(input: String) {
        _uiState.update { it.copy(stepInput = input) }
    }

    fun addStep() {
        val stepText = _uiState.value.stepInput.trim()
        if (stepText.isNotBlank()) {
            val newStep = RecipeStep(
                uuid = UUID.randomUUID(),
                orderIndex = _uiState.value.steps.size,
                instruction = stepText
            )
            _uiState.update {
                it.copy(
                    steps = it.steps + newStep,
                    stepInput = ""
                )
            }
            validateForm()
        }
    }

    fun removeStep(step: RecipeStep) {
        _uiState.update { state ->
            val updatedSteps = state.steps.filter { it != step }
                .mapIndexed { index, s -> s.copy(orderIndex = index) }
            state.copy(steps = updatedSteps)
        }
        validateForm()
    }

    fun moveStepUp(step: RecipeStep) {
        val currentIndex = _uiState.value.steps.indexOf(step)
        if (currentIndex > 0) {
            val mutableSteps = _uiState.value.steps.toMutableList()
            mutableSteps.removeAt(currentIndex)
            mutableSteps.add(currentIndex - 1, step)
            _uiState.update {
                it.copy(steps = mutableSteps.mapIndexed { index, s ->
                    s.copy(orderIndex = index)
                })
            }
        }
    }

    fun moveStepDown(step: RecipeStep) {
        val currentIndex = _uiState.value.steps.indexOf(step)
        if (currentIndex < _uiState.value.steps.size - 1) {
            val mutableSteps = _uiState.value.steps.toMutableList()
            mutableSteps.removeAt(currentIndex)
            mutableSteps.add(currentIndex + 1, step)
            _uiState.update {
                it.copy(steps = mutableSteps.mapIndexed { index, s ->
                    s.copy(orderIndex = index)
                })
            }
        }
    }

    // Tag Input
    fun onTagInputChange(input: String) {
        _uiState.update {
            it.copy(
                tagInput = input,
                tagSuggestions = if (input.isNotBlank()) {
                    allTags.filter { tag ->
                        tag.contains(input, ignoreCase = true) &&
                                it.selectedTags.none { selected -> selected.displayName == tag }
                    }.take(5)
                } else {
                    emptyList()
                }
            )
        }
    }

    fun addTag(tagName: String = _uiState.value.tagInput.trim()) {
        if (tagName.isNotBlank() && _uiState.value.selectedTags.none { it.displayName == tagName }) {
            val newTag = Tag(
                uuid = UUID.randomUUID(),
                displayName = tagName
            )
            _uiState.update {
                it.copy(
                    selectedTags = it.selectedTags + newTag,
                    tagInput = "",
                    tagSuggestions = emptyList()
                )
            }
        }
    }

    fun removeTag(tag: Tag) {
        _uiState.update {
            it.copy(selectedTags = it.selectedTags - tag)
        }
    }

    // Label Input
    fun onLabelInputChange(input: String) {
        _uiState.update {
            it.copy(
                labelInput = input,
                labelSuggestions = if (input.isNotBlank()) {
                    allLabels.filter { label ->
                        label.contains(input, ignoreCase = true) &&
                                it.selectedLabels.none { selected -> selected.displayName == label }
                    }.take(5)
                } else {
                    emptyList()
                }
            )
        }
    }

    fun addLabel(labelName: String = _uiState.value.labelInput.trim()) {
        if (labelName.isNotBlank() && _uiState.value.selectedLabels.none { it.displayName == labelName }) {
            val newLabel = Label(
                uuid = UUID.randomUUID(),
                displayName = labelName
            )
            _uiState.update {
                it.copy(
                    selectedLabels = it.selectedLabels + newLabel,
                    labelInput = "",
                    labelSuggestions = emptyList()
                )
            }
        }
    }

    fun removeLabel(label: Label) {
        _uiState.update {
            it.copy(selectedLabels = it.selectedLabels - label)
        }
    }

    // Form Validation
    private fun validateForm() {
        val state = _uiState.value
        val isValid = state.title.isNotBlank() &&
                state.description.isNotBlank() &&
                state.prepTimeMinutes.isNotBlank() &&
                state.cookTimeMinutes.isNotBlank() &&
                state.servings.isNotBlank() &&
                state.selectedIngredients.isNotEmpty() &&
                state.steps.isNotEmpty()

        _uiState.update { it.copy(isFormValid = isValid) }
    }

    // Save Recipe
    fun saveRecipe(currentUser: User, onSuccess: () -> Unit) {
        if (!_uiState.value.isFormValid) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }

            try {
                val state = _uiState.value
                val recipe = Recipe(
                    uuid = UUID.randomUUID(),
                    title = state.title,
                    description = state.description,
                    imageUrl = state.imageUrl,
                    imageUrlThumbnail = state.imageUrl,
                    prepTimeMinutes = state.prepTimeMinutes.toInt(),
                    cookTimeMinutes = state.cookTimeMinutes.toInt(),
                    servings = state.servings.toInt(),
                    creator = currentUser,
                    recipeExternalUrl = state.externalUrl.ifBlank { null },
                    ingredients = state.selectedIngredients,
                    steps = state.steps,
                    tags = state.selectedTags,
                    labels = state.selectedLabels,
                    updatedAt = System.currentTimeMillis()
                )

                recipesRepository.createRecipe(recipe)

                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: Exception) {
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
