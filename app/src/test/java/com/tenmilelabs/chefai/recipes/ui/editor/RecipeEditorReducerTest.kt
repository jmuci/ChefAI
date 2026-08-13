package com.tenmilelabs.chefai.recipes.ui.editor

import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.recipes.domain.model.EditorMode
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RecipeEditorReducerTest {

    private val emptyState = RecipeEditorState()

    // --- Title ---

    @Test
    fun `TitleChanged updates title`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.TitleChanged("Pasta"))
        assertEquals("Pasta", result.recipeFields.title)
    }

    @Test
    fun `TitleChanged marks dirty in create mode`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.TitleChanged("Pasta"))
        assertTrue(result.isDirty)
    }

    // --- Description ---

    @Test
    fun `DescriptionChanged updates description`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.DescriptionChanged("Tasty"))
        assertEquals("Tasty", result.recipeFields.description)
    }

    // --- Numeric field validation ---

    @Test
    fun `PrepTimeChanged accepts valid integer string`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.PrepTimeChanged("15"))
        assertEquals("15", result.recipeFields.prepTimeMinutes)
    }

    @Test
    fun `PrepTimeChanged rejects non-numeric input`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.PrepTimeChanged("abc"))
        assertEquals("", result.recipeFields.prepTimeMinutes) // unchanged
    }

    @Test
    fun `PrepTimeChanged accepts empty string`() {
        val state = emptyState.copy(recipeFields = RecipeFields(prepTimeMinutes = "10"))
        val result = RecipeEditorReducer.reduce(state, EditorAction.PrepTimeChanged(""))
        assertEquals("", result.recipeFields.prepTimeMinutes)
    }

    @Test
    fun `CookTimeChanged rejects non-numeric input`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.CookTimeChanged("xyz"))
        assertEquals("", result.recipeFields.cookTimeMinutes)
    }

    @Test
    fun `ServingsChanged rejects non-numeric input`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.ServingsChanged("many"))
        assertEquals("", result.recipeFields.servings)
    }

    // --- Image ---

    @Test
    fun `ImageSelected leaves state untouched — the ViewModel stores the bytes first`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.ImageSelected("content://img"))
        assertEquals(emptyState, result)
    }

    @Test
    fun `PickedImageStored records the stored path`() {
        val result = RecipeEditorReducer.reduce(
            emptyState,
            EditorAction.PickedImageStored("/data/recipe_images/abc")
        )
        assertEquals("/data/recipe_images/abc", result.recipeFields.localImagePath)
    }

    @Test
    fun `PickedImageStored clears imageUrl so the image reads as user-authored`() {
        val state = emptyState.copy(
            recipeFields = RecipeFields(imageUrl = "https://example.com/scraped.jpg")
        )
        val result = RecipeEditorReducer.reduce(
            state,
            EditorAction.PickedImageStored("/data/recipe_images/abc")
        )
        assertEquals("", result.recipeFields.imageUrl)
    }

    @Test
    fun `ClearImage clears both the stored path and the url`() {
        val state = emptyState.copy(
            recipeFields = RecipeFields(
                imageUrl = "https://example.com/x.jpg",
                localImagePath = "/data/recipe_images/abc"
            )
        )
        val result = RecipeEditorReducer.reduce(state, EditorAction.ClearImage)
        assertEquals(null, result.recipeFields.localImagePath)
        assertEquals("", result.recipeFields.imageUrl)
    }

    // --- Ingredients ---

    @Test
    fun `IngredientSelected adds ingredient when quantity is valid`() {
        val state = emptyState.copy(
            ingredients = IngredientsFields(quantity = "200", unit = "grams")
        )
        val ingredientId = UUID.randomUUID()
        val result = RecipeEditorReducer.reduce(
            state,
            EditorAction.IngredientSelected("Flour", ingredientId)
        )

        assertEquals(1, result.ingredients.selectedIngredients.size)
        val added = result.ingredients.selectedIngredients.first()
        assertEquals("Flour", added.ingredientDisplayName)
        assertEquals(200.0, added.quantity, 0.001)
        assertEquals("grams", added.unit)
    }

    @Test
    fun `IngredientSelected clears input fields after adding`() {
        val state = emptyState.copy(
            ingredients = IngredientsFields(input = "Flour", quantity = "200", unit = "grams")
        )
        val result = RecipeEditorReducer.reduce(
            state,
            EditorAction.IngredientSelected("Flour", UUID.randomUUID())
        )

        assertEquals("", result.ingredients.input)
        assertEquals("", result.ingredients.quantity)
        assertEquals("", result.ingredients.unit)
    }

    @Test
    fun `IngredientSelected does nothing when quantity is blank`() {
        val state = emptyState.copy(
            ingredients = IngredientsFields(quantity = "")
        )
        val result = RecipeEditorReducer.reduce(
            state,
            EditorAction.IngredientSelected("Flour", UUID.randomUUID())
        )

        assertTrue(result.ingredients.selectedIngredients.isEmpty())
    }

    @Test
    fun `IngredientQuantityChanged rejects non-numeric input`() {
        val result = RecipeEditorReducer.reduce(
            emptyState,
            EditorAction.IngredientQuantityChanged("abc")
        )
        assertEquals("", result.ingredients.quantity)
    }

    @Test
    fun `RemoveIngredient removes the specified ingredient`() {
        val ingredient = RecipeIngredient(
            UUID.randomUUID(), "Flour", 200.0, "grams", null, null, null
        )
        val state = emptyState.copy(
            ingredients = IngredientsFields(selectedIngredients = listOf(ingredient))
        )
        val result = RecipeEditorReducer.reduce(state, EditorAction.RemoveIngredient(ingredient))

        assertTrue(result.ingredients.selectedIngredients.isEmpty())
    }

    // --- Steps ---

    @Test
    fun `AddStep appends step and clears input`() {
        val state = emptyState.copy(steps = StepsFields(input = "Boil water"))
        val result = RecipeEditorReducer.reduce(state, EditorAction.AddStep)

        assertEquals(1, result.steps.steps.size)
        assertEquals("Boil water", result.steps.steps.first().instruction)
        assertEquals(0, result.steps.steps.first().orderIndex)
        assertEquals("", result.steps.input)
    }

    @Test
    fun `AddStep does nothing when input is blank`() {
        val state = emptyState.copy(steps = StepsFields(input = "   "))
        val result = RecipeEditorReducer.reduce(state, EditorAction.AddStep)

        assertTrue(result.steps.steps.isEmpty())
    }

    @Test
    fun `RemoveStep removes and reindexes`() {
        val step0 = RecipeStep(UUID.randomUUID(), 0, "First")
        val step1 = RecipeStep(UUID.randomUUID(), 1, "Second")
        val step2 = RecipeStep(UUID.randomUUID(), 2, "Third")
        val state = emptyState.copy(steps = StepsFields(steps = listOf(step0, step1, step2)))

        val result = RecipeEditorReducer.reduce(state, EditorAction.RemoveStep(step1))

        assertEquals(2, result.steps.steps.size)
        assertEquals(0, result.steps.steps[0].orderIndex)
        assertEquals(1, result.steps.steps[1].orderIndex)
        assertEquals("First", result.steps.steps[0].instruction)
        assertEquals("Third", result.steps.steps[1].instruction)
    }

    @Test
    fun `MoveStepUp swaps step with previous`() {
        val step0 = RecipeStep(UUID.randomUUID(), 0, "First")
        val step1 = RecipeStep(UUID.randomUUID(), 1, "Second")
        val state = emptyState.copy(steps = StepsFields(steps = listOf(step0, step1)))

        val result = RecipeEditorReducer.reduce(state, EditorAction.MoveStepUp(step1))

        assertEquals("Second", result.steps.steps[0].instruction)
        assertEquals("First", result.steps.steps[1].instruction)
        assertEquals(0, result.steps.steps[0].orderIndex)
        assertEquals(1, result.steps.steps[1].orderIndex)
    }

    @Test
    fun `MoveStepUp does nothing for first step`() {
        val step0 = RecipeStep(UUID.randomUUID(), 0, "First")
        val step1 = RecipeStep(UUID.randomUUID(), 1, "Second")
        val state = emptyState.copy(steps = StepsFields(steps = listOf(step0, step1)))

        val result = RecipeEditorReducer.reduce(state, EditorAction.MoveStepUp(step0))

        assertEquals("First", result.steps.steps[0].instruction)
        assertEquals("Second", result.steps.steps[1].instruction)
    }

    @Test
    fun `MoveStepDown swaps step with next`() {
        val step0 = RecipeStep(UUID.randomUUID(), 0, "First")
        val step1 = RecipeStep(UUID.randomUUID(), 1, "Second")
        val state = emptyState.copy(steps = StepsFields(steps = listOf(step0, step1)))

        val result = RecipeEditorReducer.reduce(state, EditorAction.MoveStepDown(step0))

        assertEquals("Second", result.steps.steps[0].instruction)
        assertEquals("First", result.steps.steps[1].instruction)
    }

    @Test
    fun `MoveStepDown does nothing for last step`() {
        val step0 = RecipeStep(UUID.randomUUID(), 0, "First")
        val step1 = RecipeStep(UUID.randomUUID(), 1, "Second")
        val state = emptyState.copy(steps = StepsFields(steps = listOf(step0, step1)))

        val result = RecipeEditorReducer.reduce(state, EditorAction.MoveStepDown(step1))

        assertEquals("First", result.steps.steps[0].instruction)
        assertEquals("Second", result.steps.steps[1].instruction)
    }

    // --- Tags ---

    @Test
    fun `AddTag adds tag and clears input`() {
        val tag = Tag(UUID.randomUUID(), "italian")
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.AddTag(tag))

        assertEquals(1, result.tags.selectedTags.size)
        assertEquals("italian", result.tags.selectedTags.first().displayName)
        assertEquals("", result.tags.input)
    }

    @Test
    fun `AddTag does not add duplicate tag`() {
        val tag = Tag(UUID.randomUUID(), "italian")
        val state = emptyState.copy(
            tags = TagsFields(selectedTags = listOf(tag))
        )
        val duplicate = Tag(UUID.randomUUID(), "italian")

        val result = RecipeEditorReducer.reduce(state, EditorAction.AddTag(duplicate))
        assertEquals(1, result.tags.selectedTags.size)
    }

    @Test
    fun `RemoveTag removes specified tag`() {
        val tag = Tag(UUID.randomUUID(), "italian")
        val state = emptyState.copy(
            tags = TagsFields(selectedTags = listOf(tag))
        )
        val result = RecipeEditorReducer.reduce(state, EditorAction.RemoveTag(tag))
        assertTrue(result.tags.selectedTags.isEmpty())
    }

    // --- Labels ---

    @Test
    fun `AddLabel adds label and clears input`() {
        val label = Label(UUID.randomUUID(), "Dinner")
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.AddLabel(label))

        assertEquals(1, result.labels.selectedLabels.size)
        assertEquals("Dinner", result.labels.selectedLabels.first().displayName)
    }

    @Test
    fun `AddLabel does not add duplicate label`() {
        val label = Label(UUID.randomUUID(), "Dinner")
        val state = emptyState.copy(
            labels = LabelsFields(selectedLabels = listOf(label))
        )
        val result = RecipeEditorReducer.reduce(state, EditorAction.AddLabel(Label(UUID.randomUUID(), "Dinner")))
        assertEquals(1, result.labels.selectedLabels.size)
    }

    // --- Form Validation ---

    @Test
    fun `isFormValid requires all required fields`() {
        val ingredient = RecipeIngredient(
            UUID.randomUUID(), "Flour", 200.0, "grams", null, null, null
        )
        val step = RecipeStep(UUID.randomUUID(), 0, "Mix")

        val state = emptyState.copy(
            recipeFields = RecipeFields(
                title = "Pancakes",
                description = "Fluffy",
                prepTimeMinutes = "10",
                cookTimeMinutes = "15",
                servings = "4",
            ),
            ingredients = IngredientsFields(selectedIngredients = listOf(ingredient)),
            steps = StepsFields(steps = listOf(step)),
        )

        // Trigger revalidation via a field change
        val result = RecipeEditorReducer.reduce(state, EditorAction.TitleChanged("Pancakes"))
        assertTrue(result.isFormValid)
    }

    @Test
    fun `isFormValid false when title is blank`() {
        val ingredient = RecipeIngredient(
            UUID.randomUUID(), "Flour", 200.0, "grams", null, null, null
        )
        val step = RecipeStep(UUID.randomUUID(), 0, "Mix")

        val state = emptyState.copy(
            recipeFields = RecipeFields(
                title = "",
                description = "Fluffy",
                prepTimeMinutes = "10",
                cookTimeMinutes = "15",
                servings = "4",
            ),
            ingredients = IngredientsFields(selectedIngredients = listOf(ingredient)),
            steps = StepsFields(steps = listOf(step)),
        )

        val result = RecipeEditorReducer.reduce(state, EditorAction.DescriptionChanged("Fluffy"))
        assertFalse(result.isFormValid)
    }

    @Test
    fun `isFormValid false when no ingredients`() {
        val step = RecipeStep(UUID.randomUUID(), 0, "Mix")
        val state = emptyState.copy(
            recipeFields = RecipeFields(
                title = "Pancakes",
                description = "Fluffy",
                prepTimeMinutes = "10",
                cookTimeMinutes = "15",
                servings = "4",
            ),
            steps = StepsFields(steps = listOf(step)),
        )

        val result = RecipeEditorReducer.reduce(state, EditorAction.TitleChanged("Pancakes"))
        assertFalse(result.isFormValid)
    }

    @Test
    fun `isFormValid false when no steps`() {
        val ingredient = RecipeIngredient(
            UUID.randomUUID(), "Flour", 200.0, "grams", null, null, null
        )
        val state = emptyState.copy(
            recipeFields = RecipeFields(
                title = "Pancakes",
                description = "Fluffy",
                prepTimeMinutes = "10",
                cookTimeMinutes = "15",
                servings = "4",
            ),
            ingredients = IngredientsFields(selectedIngredients = listOf(ingredient)),
        )

        val result = RecipeEditorReducer.reduce(state, EditorAction.TitleChanged("Pancakes"))
        assertFalse(result.isFormValid)
    }

    // --- Dirty Tracking ---

    @Test
    fun `isDirty false in create mode with empty form`() {
        val result = RecipeEditorReducer.reduce(
            emptyState,
            EditorAction.ExternalUrlChanged("")
        )
        assertFalse(result.isDirty)
    }

    @Test
    fun `isDirty true in create mode when title is set`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.TitleChanged("Pasta"))
        assertTrue(result.isDirty)
    }

    @Test
    fun `isDirty false in edit mode when state matches original`() {
        val recipeId = UUID.randomUUID()
        val originalDraft = RecipeDraft(
            recipeId = recipeId,
            isNewRecipe = false,
            title = "Pasta",
            description = "Good",
            prepTimeMinutes = "10",
            cookTimeMinutes = "20",
            servings = "4",
            version = 1,
            updatedAt = 1000L,
        )

        val state = RecipeEditorState(
            mode = EditorMode.Edit(recipeId),
            recipeId = recipeId,
            recipeFields = RecipeFields(
                title = "Pasta",
                description = "Good",
                prepTimeMinutes = "10",
                cookTimeMinutes = "20",
                servings = "4",
            ),
            originalDraft = originalDraft,
        )

        // Trigger markDirty by changing title back to same value
        val result = RecipeEditorReducer.reduce(state, EditorAction.TitleChanged("Pasta"))
        assertFalse(result.isDirty)
    }

    @Test
    fun `isDirty true in edit mode when state differs from original`() {
        val recipeId = UUID.randomUUID()
        val originalDraft = RecipeDraft(
            recipeId = recipeId,
            isNewRecipe = false,
            title = "Pasta",
            description = "Good",
            prepTimeMinutes = "10",
            cookTimeMinutes = "20",
            servings = "4",
            version = 1,
            updatedAt = 1000L,
        )

        val state = RecipeEditorState(
            mode = EditorMode.Edit(recipeId),
            recipeId = recipeId,
            recipeFields = RecipeFields(
                title = "Pasta",
                description = "Good",
                prepTimeMinutes = "10",
                cookTimeMinutes = "20",
                servings = "4",
            ),
            originalDraft = originalDraft,
        )

        val result = RecipeEditorReducer.reduce(state, EditorAction.TitleChanged("Risotto"))
        assertTrue(result.isDirty)
    }

    // --- Delete dialog ---

    @Test
    fun `Delete shows confirmation dialog`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.Delete)
        assertTrue(result.showDeleteConfirmation)
    }

    @Test
    fun `DismissDeleteDialog hides confirmation dialog`() {
        val state = emptyState.copy(showDeleteConfirmation = true)
        val result = RecipeEditorReducer.reduce(state, EditorAction.DismissDeleteDialog)
        assertFalse(result.showDeleteConfirmation)
    }

    @Test
    fun `ClearError clears saveError`() {
        val state = emptyState.copy(saveError = "Something went wrong")
        val result = RecipeEditorReducer.reduce(state, EditorAction.ClearError)
        assertEquals(null, result.saveError)
    }

    // --- Side-effect-only actions don't change state ---

    @Test
    fun `Save does not change state`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.Save)
        assertEquals(emptyState, result)
    }

    @Test
    fun `ConfirmDelete does not change state`() {
        val result = RecipeEditorReducer.reduce(emptyState, EditorAction.ConfirmDelete)
        assertEquals(emptyState, result)
    }
}
