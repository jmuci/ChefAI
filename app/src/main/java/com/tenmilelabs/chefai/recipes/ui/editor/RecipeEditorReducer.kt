package com.tenmilelabs.chefai.recipes.ui.editor

import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import java.util.UUID

/**
 * Pure reducer for the recipe editor.
 * Maps `(RecipeEditorState, EditorAction) → RecipeEditorState` with no side effects.
 * All side-effect-producing actions (Save, Delete, etc.) return the state unchanged;
 * the ViewModel handles those in [RecipeEditorViewModel.dispatch].
 */
object RecipeEditorReducer {

    fun reduce(state: RecipeEditorState, action: EditorAction): RecipeEditorState = when (action) {

        // --- Recipe scalar fields ---

        is EditorAction.TitleChanged -> state.copy(
            recipeFields = state.recipeFields.copy(title = action.title)
        ).revalidate().markDirty()

        is EditorAction.DescriptionChanged -> state.copy(
            recipeFields = state.recipeFields.copy(description = action.description)
        ).revalidate().markDirty()

        is EditorAction.PrepTimeChanged -> {
            if (action.time.isEmpty() || action.time.toIntOrNull() != null) {
                state.copy(
                    recipeFields = state.recipeFields.copy(prepTimeMinutes = action.time)
                ).revalidate().markDirty()
            } else state
        }

        is EditorAction.CookTimeChanged -> {
            if (action.time.isEmpty() || action.time.toIntOrNull() != null) {
                state.copy(
                    recipeFields = state.recipeFields.copy(cookTimeMinutes = action.time)
                ).revalidate().markDirty()
            } else state
        }

        is EditorAction.ServingsChanged -> {
            if (action.servings.isEmpty() || action.servings.toIntOrNull() != null) {
                state.copy(
                    recipeFields = state.recipeFields.copy(servings = action.servings)
                ).revalidate().markDirty()
            } else state
        }

        is EditorAction.ExternalUrlChanged -> state.copy(
            recipeFields = state.recipeFields.copy(externalUrl = action.url)
        ).markDirty()

        is EditorAction.PrivacyChanged -> state.copy(
            recipeFields = state.recipeFields.copy(privacy = action.privacy)
        ).markDirty()

        // localImagePath is cleared alongside the field it was cached for — otherwise the user
        // keeps seeing the stale on-device copy from before their edit.
        is EditorAction.ImageUrlChanged -> state.copy(
            recipeFields = state.recipeFields.copy(imageUrl = action.url, localImagePath = null)
        ).markDirty()

        // The ViewModel copies the picked photo into RecipeImageStore and reports back via
        // PickedImageStored — there's nothing for the reducer to do with the raw content:// itself.
        is EditorAction.ImageSelected -> state

        // A stored photo and a remote URL are alternatives, never both: the editor offers them as an
        // either/or, and everything downstream — the backfill worker most of all — reads a blank
        // imageUrl as "this image is the user's own, don't try to re-derive it". See ADR-011.
        is EditorAction.PickedImageStored -> state.copy(
            recipeFields = state.recipeFields.copy(
                imageUrl = "",
                localImagePath = action.localImagePath
            )
        ).markDirty()

        is EditorAction.ClearImage -> state.copy(
            recipeFields = state.recipeFields.copy(imageUrl = "", localImagePath = null)
        ).markDirty()

        // --- Ingredients ---

        is EditorAction.IngredientInputChanged -> state.copy(
            ingredients = state.ingredients.copy(input = action.input)
        ) // suggestions updated by ViewModel

        is EditorAction.IngredientQuantityChanged -> {
            if (action.quantity.isEmpty() || action.quantity.toDoubleOrNull() != null) {
                state.copy(
                    ingredients = state.ingredients.copy(quantity = action.quantity)
                )
            } else state
        }

        is EditorAction.IngredientUnitChanged -> state.copy(
            ingredients = state.ingredients.copy(unit = action.unit)
        )

        is EditorAction.IngredientSelected -> {
            val fields = state.ingredients
            if (fields.quantity.isNotBlank() && fields.quantity.toDoubleOrNull() != null) {
                val ingredient = RecipeIngredient(
                    ingredientId = action.ingredientId,
                    ingredientDisplayName = action.name,
                    quantity = fields.quantity.toDouble(),
                    unit = fields.unit.ifBlank { "unit" },
                    allergenName = null,
                    srcCategory = null,
                    srcSubcategory = null,
                )
                state.copy(
                    ingredients = fields.copy(
                        selectedIngredients = fields.selectedIngredients + ingredient,
                        input = "",
                        quantity = "",
                        unit = "",
                        suggestions = emptyList(),
                    )
                ).revalidate().markDirty()
            } else state
        }

        is EditorAction.RemoveIngredient -> state.copy(
            ingredients = state.ingredients.copy(
                selectedIngredients = state.ingredients.selectedIngredients - action.ingredient
            )
        ).revalidate().markDirty()

        // --- Steps ---

        is EditorAction.StepInputChanged -> state.copy(
            steps = state.steps.copy(input = action.input)
        )

        is EditorAction.AddStep -> {
            val stepText = state.steps.input.trim()
            if (stepText.isNotBlank()) {
                val newStep = RecipeStep(
                    uuid = UUID.randomUUID(),
                    orderIndex = state.steps.steps.size,
                    instruction = stepText,
                )
                state.copy(
                    steps = state.steps.copy(
                        steps = state.steps.steps + newStep,
                        input = "",
                    )
                ).revalidate().markDirty()
            } else state
        }

        is EditorAction.RemoveStep -> {
            val updatedSteps = state.steps.steps
                .filter { it != action.step }
                .mapIndexed { index, s -> s.copy(orderIndex = index) }
            state.copy(
                steps = state.steps.copy(steps = updatedSteps)
            ).revalidate().markDirty()
        }

        is EditorAction.MoveStepUp -> {
            val currentIndex = state.steps.steps.indexOf(action.step)
            if (currentIndex > 0) {
                val mutableSteps = state.steps.steps.toMutableList()
                mutableSteps.removeAt(currentIndex)
                mutableSteps.add(currentIndex - 1, action.step)
                state.copy(
                    steps = state.steps.copy(
                        steps = mutableSteps.mapIndexed { index, s -> s.copy(orderIndex = index) }
                    )
                ).markDirty()
            } else state
        }

        is EditorAction.MoveStepDown -> {
            val currentIndex = state.steps.steps.indexOf(action.step)
            if (currentIndex in 0 until state.steps.steps.size - 1) {
                val mutableSteps = state.steps.steps.toMutableList()
                mutableSteps.removeAt(currentIndex)
                mutableSteps.add(currentIndex + 1, action.step)
                state.copy(
                    steps = state.steps.copy(
                        steps = mutableSteps.mapIndexed { index, s -> s.copy(orderIndex = index) }
                    )
                ).markDirty()
            } else state
        }

        // --- Tags ---

        is EditorAction.TagInputChanged -> state.copy(
            tags = state.tags.copy(input = action.input)
        ) // suggestions updated by ViewModel

        is EditorAction.AddTag -> {
            if (state.tags.selectedTags.none { it.displayName == action.tag.displayName }) {
                state.copy(
                    tags = state.tags.copy(
                        selectedTags = state.tags.selectedTags + action.tag,
                        input = "",
                        suggestions = emptyList(),
                    )
                ).markDirty()
            } else state
        }

        is EditorAction.RemoveTag -> state.copy(
            tags = state.tags.copy(selectedTags = state.tags.selectedTags - action.tag)
        ).markDirty()

        // --- Labels ---

        is EditorAction.LabelInputChanged -> state.copy(
            labels = state.labels.copy(input = action.input)
        ) // suggestions updated by ViewModel

        is EditorAction.AddLabel -> {
            if (state.labels.selectedLabels.none { it.displayName == action.label.displayName }) {
                state.copy(
                    labels = state.labels.copy(
                        selectedLabels = state.labels.selectedLabels + action.label,
                        input = "",
                        suggestions = emptyList(),
                    )
                ).markDirty()
            } else state
        }

        is EditorAction.RemoveLabel -> state.copy(
            labels = state.labels.copy(selectedLabels = state.labels.selectedLabels - action.label)
        ).markDirty()

        // --- Lifecycle (side-effect-only actions) ---

        is EditorAction.Delete -> state.copy(showDeleteConfirmation = true)
        is EditorAction.DismissDeleteDialog -> state.copy(showDeleteConfirmation = false)
        is EditorAction.ClearError -> state.copy(saveError = null)

        // Save, ConfirmDelete: handled by ViewModel, no state change here
        is EditorAction.Save,
        is EditorAction.ConfirmDelete -> state
    }

    /**
     * Recomputes [RecipeEditorState.isFormValid] based on required fields.
     * Exposed for the ViewModel to call after populating state outside the
     * reducer (e.g. loading an existing recipe or restoring a draft).
     */
    fun recomputeValidity(state: RecipeEditorState): RecipeEditorState = state.revalidate()

    private fun RecipeEditorState.revalidate(): RecipeEditorState {
        val isValid = recipeFields.title.isNotBlank() &&
            recipeFields.description.isNotBlank() &&
            recipeFields.prepTimeMinutes.isNotBlank() &&
            recipeFields.cookTimeMinutes.isNotBlank() &&
            recipeFields.servings.isNotBlank() &&
            ingredients.selectedIngredients.isNotEmpty() &&
            steps.steps.isNotEmpty()
        return copy(isFormValid = isValid)
    }

    /**
     * Computes [RecipeEditorState.isDirty] by comparing current form state to the original snapshot.
     * - Create mode: dirty if any required field is non-empty.
     * - Edit mode: dirty if current fields differ from [originalDraft].
     */
    private fun RecipeEditorState.markDirty(): RecipeEditorState {
        if (originalDraft == null) {
            // Create mode
            val dirty = recipeFields.title.isNotBlank() ||
                recipeFields.description.isNotBlank() ||
                ingredients.selectedIngredients.isNotEmpty() ||
                steps.steps.isNotEmpty()
            return copy(isDirty = dirty)
        }
        // Edit mode: compare current form state to the original snapshot.
        // Normalize updatedAt so that timestamp differences don't cause false positives.
        val currentDraft = toRecipeDraft().copy(updatedAt = originalDraft.updatedAt)
        return copy(isDirty = currentDraft != originalDraft)
    }
}

/**
 * Converts the current editor state into a [RecipeDraft] for persistence or comparison.
 */
fun RecipeEditorState.toRecipeDraft(): RecipeDraft = RecipeDraft(
    recipeId = recipeId,
    isNewRecipe = mode is com.tenmilelabs.chefai.recipes.domain.model.EditorMode.Create,
    title = recipeFields.title,
    description = recipeFields.description,
    imageUrl = recipeFields.imageUrl,
    localImagePath = recipeFields.localImagePath,
    prepTimeMinutes = recipeFields.prepTimeMinutes,
    cookTimeMinutes = recipeFields.cookTimeMinutes,
    servings = recipeFields.servings,
    externalUrl = recipeFields.externalUrl,
    privacy = recipeFields.privacy,
    ingredients = ingredients.selectedIngredients,
    steps = steps.steps,
    tags = tags.selectedTags,
    labels = labels.selectedLabels,
    version = version,
    updatedAt = System.currentTimeMillis(),
)
