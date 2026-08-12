package com.tenmilelabs.chefai.recipes.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.collections.data.repository.FakeCollectionsRepository
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDraftDao
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.data.repository.FakeMetadataRepository
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.testutil.recipe1
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraftEntity
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipesRepository
import com.tenmilelabs.chefai.recipes.domain.model.EditorMode
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeEditorViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var recipesRepository: FakeRecipesRepository
    private lateinit var collectionsRepository: FakeCollectionsRepository
    private lateinit var metadataRepository: FakeMetadataRepository
    private lateinit var recipeDraftDao: FakeRecipeDraftDao

    @Before
    fun setup() {
        recipesRepository = FakeRecipesRepository()
        collectionsRepository = FakeCollectionsRepository()
        metadataRepository = FakeMetadataRepository()
        recipeDraftDao = FakeRecipeDraftDao()
    }

    private fun createViewModel(
        recipeId: String? = null,
        draftId: String? = null,
    ): RecipeEditorViewModel {
        val savedStateHandle = SavedStateHandle().apply {
            if (recipeId != null) set(AppDestinationArgs.RECIPE_ID_ARG, recipeId)
            if (draftId != null) set(AppDestinationArgs.DRAFT_ID_ARG, draftId)
        }
        return RecipeEditorViewModel(
            recipesRepository = recipesRepository,
            collectionsRepository = collectionsRepository,
            metadataRepository = metadataRepository,
            sessionManager = createTestSessionManager(
                testScope = TestScope(StandardTestDispatcher()),
            ),
            recipeDraftDao = recipeDraftDao,
            ioDispatcher = mainCoroutineRule.testDispatcher,
            savedStateHandle = savedStateHandle,
        )
    }

    // --- Mode Detection ---

    @Test
    fun `create mode when no recipeId in SavedStateHandle`() = runTest {
        val vm = createViewModel()
        assertEquals(EditorMode.Create, vm.mode)
        assertFalse(vm.state.value.isLoading)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `edit mode when recipeId present in SavedStateHandle`() = runTest {
        val recipeId = recipe1.uuid.toString()
        recipesRepository.addStoredRecipe(recipe1)

        val vm = createViewModel(recipeId = recipeId)
        assertTrue(vm.mode is EditorMode.Edit)
        assertEquals(recipe1.uuid, (vm.mode as EditorMode.Edit).recipeId)
        vm.viewModelScope.cancel()
    }

    // --- Seeded Draft (recipe URL import hand-off) ---

    @Test
    fun `draftId arg stays in create mode and adopts the draft id`() = runTest {
        val draftId = UuidV7Generator.newId()

        val vm = createViewModel(draftId = draftId.toString())

        assertEquals(EditorMode.Create, vm.mode)
        assertEquals(draftId, vm.state.value.recipeId)
        assertFalse(vm.state.value.isLoading)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `seeded draft populates editor state and is immediately valid`() = runTest {
        val draftId = UuidV7Generator.newId()
        recipeDraftDao.saveDraft(completeImportedDraft(draftId).toRecipeDraftEntity())

        val vm = createViewModel(draftId = draftId.toString())

        val state = vm.state.value
        assertEquals("Scraped Pancakes", state.recipeFields.title)
        assertEquals("From a food blog", state.recipeFields.description)
        assertEquals("5", state.recipeFields.prepTimeMinutes)
        assertEquals("15", state.recipeFields.cookTimeMinutes)
        assertEquals("4", state.recipeFields.servings)
        assertEquals("https://example.com/pancakes", state.recipeFields.externalUrl)
        assertEquals(1, state.ingredients.selectedIngredients.size)
        assertEquals(1, state.steps.steps.size)
        // The whole point of the import hand-off: the user can save without touching anything.
        assertTrue(state.isFormValid)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `saving a seeded draft takes the create path not update`() = runTest {
        val draftId = UuidV7Generator.newId()
        recipeDraftDao.saveDraft(completeImportedDraft(draftId).toRecipeDraftEntity())

        val vm = createViewModel(draftId = draftId.toString())
        vm.dispatch(EditorAction.Save)

        assertEquals("Scraped Pancakes", recipesRepository.lastCreatedRecipe?.title)
        assertNull(recipesRepository.lastUpdatedRecipe)
        // Source URL survives the round trip into the saved recipe.
        assertEquals(
            "https://example.com/pancakes",
            recipesRepository.lastCreatedRecipe?.recipeExternalUrl,
        )
        vm.viewModelScope.cancel()
    }

    @Test
    fun `malformed recipeId arg falls back to create mode instead of crashing`() = runTest {
        val vm = createViewModel(recipeId = "not-a-uuid")

        assertEquals(EditorMode.Create, vm.mode)
        assertFalse(vm.state.value.isLoading)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `malformed draftId arg falls back to a fresh recipe id`() = runTest {
        val vm = createViewModel(draftId = "not-a-uuid")

        assertEquals(EditorMode.Create, vm.mode)
        assertNotNull(vm.state.value.recipeId)
        assertTrue(vm.state.value.recipeFields.title.isEmpty())
        vm.viewModelScope.cancel()
    }

    /** Mirrors what the recipe URL importer persists before handing off to the editor. */
    private fun completeImportedDraft(draftId: java.util.UUID) = RecipeDraft(
        recipeId = draftId,
        isNewRecipe = true,
        title = "Scraped Pancakes",
        description = "From a food blog",
        prepTimeMinutes = "5",
        cookTimeMinutes = "15",
        servings = "4",
        externalUrl = "https://example.com/pancakes",
        ingredients = listOf(
            RecipeIngredient(
                ingredientId = UuidV7Generator.newId(),
                ingredientDisplayName = "flour",
                quantity = 2.0,
                unit = "cup",
                allergenName = null,
                srcCategory = null,
                srcSubcategory = null,
            )
        ),
        steps = listOf(
            RecipeStep(
                uuid = UuidV7Generator.newId(),
                orderIndex = 0,
                instruction = "Mix everything together.",
            )
        ),
        updatedAt = 1L,
    )

    // --- Edit Mode Loading ---

    @Test
    fun `edit mode loads recipe and populates state`() = runTest {
        recipesRepository.addStoredRecipe(recipe1)

        val vm = createViewModel(recipeId = recipe1.uuid.toString())
        // UnconfinedTestDispatcher executes eagerly — loadRecipe completes immediately

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(recipe1.title, state.recipeFields.title)
        assertEquals(recipe1.description, state.recipeFields.description)
        assertEquals(recipe1.prepTimeMinutes.toString(), state.recipeFields.prepTimeMinutes)
        assertEquals(recipe1.cookTimeMinutes.toString(), state.recipeFields.cookTimeMinutes)
        assertEquals(recipe1.servings.toString(), state.recipeFields.servings)
        assertEquals(recipe1.steps.size, state.steps.steps.size)
        assertNotNull(state.originalDraft)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `edit mode restores draft if one exists`() = runTest {
        recipesRepository.addStoredRecipe(recipe1)

        // Pre-save a draft with modified title
        val draft = RecipeDraft(
            recipeId = recipe1.uuid,
            isNewRecipe = false,
            title = "Modified Title",
            description = recipe1.description,
            prepTimeMinutes = recipe1.prepTimeMinutes.toString(),
            cookTimeMinutes = recipe1.cookTimeMinutes.toString(),
            servings = recipe1.servings.toString(),
            version = recipe1.version,
            updatedAt = System.currentTimeMillis(),
        )
        recipeDraftDao.saveDraft(draft.toRecipeDraftEntity())

        val vm = createViewModel(recipeId = recipe1.uuid.toString())

        assertEquals("Modified Title", vm.state.value.recipeFields.title)
        vm.viewModelScope.cancel()
    }

    // --- Save ---

    @Test
    fun `save in create mode calls createRecipe`() = runTest {
        val vm = createViewModel()

        // Fill required fields
        vm.dispatch(EditorAction.TitleChanged("New Recipe"))
        vm.dispatch(EditorAction.DescriptionChanged("Description"))
        vm.dispatch(EditorAction.PrepTimeChanged("10"))
        vm.dispatch(EditorAction.CookTimeChanged("20"))
        vm.dispatch(EditorAction.ServingsChanged("4"))
        vm.dispatch(EditorAction.IngredientQuantityChanged("200"))
        vm.dispatch(EditorAction.IngredientSelected("Flour", java.util.UUID.randomUUID()))
        vm.dispatch(EditorAction.StepInputChanged("Mix"))
        vm.dispatch(EditorAction.AddStep)

        assertTrue(vm.state.value.isFormValid)

        vm.dispatch(EditorAction.Save)

        assertNotNull(recipesRepository.lastCreatedRecipe)
        assertEquals("New Recipe", recipesRepository.lastCreatedRecipe?.title)
        assertFalse(vm.state.value.isSaving)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `save in edit mode calls updateRecipe`() = runTest {
        recipesRepository.addStoredRecipe(recipe1)

        val vm = createViewModel(recipeId = recipe1.uuid.toString())

        // Modify title
        vm.dispatch(EditorAction.TitleChanged("Updated Title"))

        assertTrue(vm.state.value.isFormValid)

        vm.dispatch(EditorAction.Save)

        assertNotNull(recipesRepository.lastUpdatedRecipe)
        assertEquals("Updated Title", recipesRepository.lastUpdatedRecipe?.title)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `save clears draft from Room`() = runTest {
        val vm = createViewModel()

        val recipeId = vm.state.value.recipeId

        // Pre-save a draft
        val draft = RecipeDraft(recipeId = recipeId, isNewRecipe = true, title = "Draft", updatedAt = 1L)
        recipeDraftDao.saveDraft(draft.toRecipeDraftEntity())
        assertNotNull(recipeDraftDao.getDraft(recipeId))

        // Fill required fields and save
        vm.dispatch(EditorAction.TitleChanged("Recipe"))
        vm.dispatch(EditorAction.DescriptionChanged("Desc"))
        vm.dispatch(EditorAction.PrepTimeChanged("5"))
        vm.dispatch(EditorAction.CookTimeChanged("10"))
        vm.dispatch(EditorAction.ServingsChanged("2"))
        vm.dispatch(EditorAction.IngredientQuantityChanged("100"))
        vm.dispatch(EditorAction.IngredientSelected("Salt", java.util.UUID.randomUUID()))
        vm.dispatch(EditorAction.StepInputChanged("Cook"))
        vm.dispatch(EditorAction.AddStep)

        vm.dispatch(EditorAction.Save)

        // Draft should be cleared
        assertNull(recipeDraftDao.getDraft(recipeId))
        vm.viewModelScope.cancel()
    }

    @Test
    fun `save does nothing when form is invalid`() = runTest {
        val vm = createViewModel()

        assertFalse(vm.state.value.isFormValid)
        vm.dispatch(EditorAction.Save)

        assertNull(recipesRepository.lastCreatedRecipe)
        assertFalse(vm.state.value.isSaving)
        vm.viewModelScope.cancel()
    }

    // --- Delete ---

    @Test
    fun `delete calls softDeleteRecipe and clears draft`() = runTest {
        recipesRepository.addStoredRecipe(recipe1)

        val vm = createViewModel(recipeId = recipe1.uuid.toString())

        // Pre-save a draft
        val draft = RecipeDraft(
            recipeId = recipe1.uuid, isNewRecipe = false, title = "Draft", updatedAt = 1L
        )
        recipeDraftDao.saveDraft(draft.toRecipeDraftEntity())

        vm.dispatch(EditorAction.ConfirmDelete)

        assertEquals(recipe1.uuid, recipesRepository.lastSoftDeletedId)
        assertNull(recipeDraftDao.getDraft(recipe1.uuid))
        assertFalse(vm.state.value.isDeleting)
        vm.viewModelScope.cancel()
    }

    // --- Dispatch delegates to reducer ---

    @Test
    fun `dispatch TitleChanged updates state via reducer`() = runTest {
        val vm = createViewModel()
        vm.dispatch(EditorAction.TitleChanged("Hello"))
        assertEquals("Hello", vm.state.value.recipeFields.title)
        vm.viewModelScope.cancel()
    }
}
