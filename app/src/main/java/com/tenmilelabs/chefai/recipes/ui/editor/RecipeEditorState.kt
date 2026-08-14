package com.tenmilelabs.chefai.recipes.ui.editor

import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.recipes.domain.model.EditorMode
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import java.util.UUID

/**
 * All possible user actions dispatched to the editor ViewModel.
 */
sealed interface EditorAction {

    // Recipe fields
    data class TitleChanged(val title: String) : EditorAction
    data class DescriptionChanged(val description: String) : EditorAction
    data class PrepTimeChanged(val time: String) : EditorAction
    data class CookTimeChanged(val time: String) : EditorAction
    data class ServingsChanged(val servings: String) : EditorAction
    data class ExternalUrlChanged(val url: String) : EditorAction
    data class PrivacyChanged(val privacy: RecipePrivacy) : EditorAction
    data class ImageUrlChanged(val url: String) : EditorAction
    data class ImageSelected(val uri: String?) : EditorAction

    /** Emitted by the ViewModel once a picked photo has been copied into app storage. */
    data class PickedImageStored(val localImagePath: String?) : EditorAction
    data object ClearImage : EditorAction

    // Ingredients
    data class IngredientInputChanged(val input: String) : EditorAction
    data class IngredientQuantityChanged(val quantity: String) : EditorAction
    data class IngredientUnitChanged(val unit: String) : EditorAction
    data class IngredientSelected(val name: String, val ingredientId: UUID) : EditorAction
    data class RemoveIngredient(val ingredient: RecipeIngredient) : EditorAction

    // Steps
    data class StepInputChanged(val input: String) : EditorAction
    data object AddStep : EditorAction
    data class RemoveStep(val step: RecipeStep) : EditorAction
    data class MoveStepUp(val step: RecipeStep) : EditorAction
    data class MoveStepDown(val step: RecipeStep) : EditorAction

    // Tags
    data class TagInputChanged(val input: String) : EditorAction
    data class AddTag(val tag: Tag) : EditorAction
    data class RemoveTag(val tag: Tag) : EditorAction

    // Labels
    data class LabelInputChanged(val input: String) : EditorAction
    data class AddLabel(val label: Label) : EditorAction
    data class RemoveLabel(val label: Label) : EditorAction

    // Lifecycle
    data object Save : EditorAction
    data object Delete : EditorAction
    data object ConfirmDelete : EditorAction
    data object DismissDeleteDialog : EditorAction
    data object ClearError : EditorAction
}

/**
 * One-shot side effects emitted by the ViewModel, consumed by the UI.
 */
sealed interface EditorEffect {
    data object NavigateBack : EditorEffect
    data object RecipeSaved : EditorEffect
    data object RecipeDeleted : EditorEffect
    data class ShowError(val message: String) : EditorEffect
}

/**
 * Immutable UI state for the recipe editor.
 */
data class RecipeEditorState(
    val mode: EditorMode = EditorMode.Create,
    val recipeId: UUID = UUID.randomUUID(),

    // Form fields — reusing existing data classes from create package
    val recipeFields: RecipeFields = RecipeFields(),
    val ingredients: IngredientsFields = IngredientsFields(),
    val steps: StepsFields = StepsFields(),
    val tags: TagsFields = TagsFields(),
    val labels: LabelsFields = LabelsFields(),

    // Status
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val saveError: String? = null,
    val isFormValid: Boolean = false,
    val isDirty: Boolean = false,

    // Original snapshot for dirty tracking (null in create mode)
    val originalDraft: RecipeDraft? = null,
    val version: Int = 1,
)
