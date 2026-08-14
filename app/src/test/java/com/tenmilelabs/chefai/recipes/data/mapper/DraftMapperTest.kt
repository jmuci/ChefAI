package com.tenmilelabs.chefai.recipes.data.mapper

import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.core.testutil.recipe1
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class DraftMapperTest {

    // --- Recipe → RecipeDraft ---

    @Test
    fun `Recipe toDraft maps all scalar fields`() {
        val draft = recipe1.toRecipeDraft()

        assertEquals(recipe1.uuid, draft.recipeId)
        assertEquals(false, draft.isNewRecipe)
        assertEquals(recipe1.title, draft.title)
        assertEquals(recipe1.description, draft.description)
        assertEquals(recipe1.imageUrl, draft.imageUrl)
        assertEquals(recipe1.prepTimeMinutes.toString(), draft.prepTimeMinutes)
        assertEquals(recipe1.cookTimeMinutes.toString(), draft.cookTimeMinutes)
        assertEquals(recipe1.servings.toString(), draft.servings)
        assertEquals(recipe1.recipeExternalUrl.orEmpty(), draft.externalUrl)
        assertEquals(recipe1.version, draft.version)
        assertEquals(recipe1.updatedAt, draft.updatedAt)
    }

    @Test
    fun `Recipe toDraft carries privacy`() {
        val draft = recipe1.toRecipeDraft()

        assertEquals(recipe1.privacy, draft.privacy)
    }

    @Test
    fun `Recipe toDraft preserves ingredients list`() {
        val draft = recipe1.toRecipeDraft()

        assertEquals(recipe1.ingredients.size, draft.ingredients.size)
        assertEquals(recipe1.ingredients, draft.ingredients)
    }

    @Test
    fun `Recipe toDraft preserves steps list`() {
        val draft = recipe1.toRecipeDraft()

        assertEquals(recipe1.steps.size, draft.steps.size)
        assertEquals(recipe1.steps, draft.steps)
    }

    @Test
    fun `Recipe toDraft preserves tags and labels`() {
        val draft = recipe1.toRecipeDraft()

        assertEquals(recipe1.tags, draft.tags)
        assertEquals(recipe1.labels, draft.labels)
    }

    // --- RecipeDraft → RecipeDraftEntity → RecipeDraft round-trip ---

    @Test
    fun `draft round-trips through entity with empty lists`() {
        val original = RecipeDraft(
            recipeId = UUID.randomUUID(),
            isNewRecipe = true,
            title = "New Recipe",
            description = "A description",
            imageUrl = "",
            prepTimeMinutes = "15",
            cookTimeMinutes = "30",
            servings = "4",
            externalUrl = "",
            ingredients = emptyList(),
            steps = emptyList(),
            tags = emptyList(),
            labels = emptyList(),
            version = 1,
            updatedAt = 12345L,
        )

        val entity = original.toRecipeDraftEntity()
        val restored = entity.toRecipeDraft()

        assertEquals(original, restored)
    }

    @Test
    fun `draft round-trips through entity with populated lists`() {
        val ingredientId = UUID.randomUUID()
        val stepId = UUID.randomUUID()
        val tagId = UUID.randomUUID()
        val labelId = UUID.randomUUID()

        val original = RecipeDraft(
            recipeId = UUID.randomUUID(),
            isNewRecipe = false,
            title = "Pasta Carbonara",
            description = "Classic Italian pasta",
            imageUrl = "https://example.com/image.jpg",
            prepTimeMinutes = "10",
            cookTimeMinutes = "20",
            servings = "2",
            externalUrl = "https://recipes.example.com/carbonara",
            ingredients = listOf(
                RecipeIngredient(
                    ingredientId = ingredientId,
                    ingredientDisplayName = "Spaghetti",
                    quantity = 200.0,
                    unit = "grams",
                    allergenName = "Gluten",
                    srcCategory = "Plant",
                    srcSubcategory = "Grain",
                )
            ),
            steps = listOf(
                RecipeStep(uuid = stepId, orderIndex = 0, instruction = "Boil water")
            ),
            tags = listOf(Tag(uuid = tagId, displayName = "italian")),
            labels = listOf(Label(uuid = labelId, displayName = "Dinner")),
            version = 3,
            updatedAt = 999_000L,
        )

        val entity = original.toRecipeDraftEntity()
        val restored = entity.toRecipeDraft()

        assertEquals(original, restored)
    }

    @Test
    fun `draft privacy round-trips through entity for every enum value`() {
        RecipePrivacy.entries.forEach { privacy ->
            val original = RecipeDraft(
                recipeId = UUID.randomUUID(),
                isNewRecipe = true,
                privacy = privacy,
                updatedAt = 1L,
            )

            val restored = original.toRecipeDraftEntity().toRecipeDraft()

            assertEquals(privacy, restored.privacy)
        }
    }

    @Test
    fun `draft entity JSON preserves null optional fields on ingredient`() {
        val original = RecipeDraft(
            recipeId = UUID.randomUUID(),
            isNewRecipe = true,
            ingredients = listOf(
                RecipeIngredient(
                    ingredientId = UUID.randomUUID(),
                    ingredientDisplayName = "Salt",
                    quantity = 1.0,
                    unit = "tsp",
                    allergenName = null,
                    srcCategory = null,
                    srcSubcategory = null,
                )
            ),
            updatedAt = 1L,
        )

        val restored = original.toRecipeDraftEntity().toRecipeDraft()

        val ingredient = restored.ingredients.first()
        assertNull(ingredient.allergenName)
        assertNull(ingredient.srcCategory)
        assertNull(ingredient.srcSubcategory)
    }

    @Test
    fun `draft entity preserves localImagePath when null`() {
        val original = RecipeDraft(
            recipeId = UUID.randomUUID(),
            isNewRecipe = true,
            localImagePath = null,
            updatedAt = 1L,
        )

        val restored = original.toRecipeDraftEntity().toRecipeDraft()
        assertNull(restored.localImagePath)
    }

    @Test
    fun `draft entity preserves localImagePath when present`() {
        val original = RecipeDraft(
            recipeId = UUID.randomUUID(),
            isNewRecipe = true,
            localImagePath = "/data/data/com.tenmilelabs.chefai/files/recipe_images/99",
            updatedAt = 1L,
        )

        val restored = original.toRecipeDraftEntity().toRecipeDraft()
        assertEquals("/data/data/com.tenmilelabs.chefai/files/recipe_images/99", restored.localImagePath)
    }

    // --- RecipeDraft → Recipe ---

    @Test
    fun `draft toRecipe maps numeric string fields to ints`() {
        val draft = RecipeDraft(
            recipeId = UUID.randomUUID(),
            isNewRecipe = true,
            title = "Test",
            description = "Desc",
            imageUrl = "url",
            prepTimeMinutes = "15",
            cookTimeMinutes = "30",
            servings = "4",
            version = 2,
            updatedAt = 5000L,
        )

        val creator = User(
            uuid = UUID.randomUUID(),
            displayName = "Chef",
            email = "chef@example.com",
            avatarUrl = "",
        )

        val recipe = draft.toRecipe(creator)

        assertEquals(draft.recipeId, recipe.uuid)
        assertEquals("Test", recipe.title)
        assertEquals("Desc", recipe.description)
        assertEquals(15, recipe.prepTimeMinutes)
        assertEquals(30, recipe.cookTimeMinutes)
        assertEquals(4, recipe.servings)
        assertEquals(creator, recipe.creator)
        assertEquals(2, recipe.version)
        assertEquals(5000L, recipe.updatedAt)
    }

    @Test
    fun `draft toRecipe honours the draft's privacy for every enum value`() {
        val creator = User(UUID.randomUUID(), "Chef", "chef@example.com", "")

        RecipePrivacy.entries.forEach { privacy ->
            val draft = RecipeDraft(
                recipeId = UUID.randomUUID(),
                isNewRecipe = true,
                privacy = privacy,
                prepTimeMinutes = "1",
                cookTimeMinutes = "1",
                servings = "1",
                updatedAt = 1L,
            )

            assertEquals(privacy, draft.toRecipe(creator).privacy)
        }
    }

    @Test
    fun `draft toRecipe maps blank externalUrl to null`() {
        val draft = RecipeDraft(
            recipeId = UUID.randomUUID(),
            isNewRecipe = true,
            externalUrl = "",
            prepTimeMinutes = "1",
            cookTimeMinutes = "1",
            servings = "1",
            updatedAt = 1L,
        )

        val creator = User(UUID.randomUUID(), "Chef", "chef@example.com", "")
        val recipe = draft.toRecipe(creator)

        assertNull(recipe.recipeExternalUrl)
    }

    @Test
    fun `draft toRecipe preserves non-blank externalUrl`() {
        val draft = RecipeDraft(
            recipeId = UUID.randomUUID(),
            isNewRecipe = true,
            externalUrl = "https://example.com",
            prepTimeMinutes = "1",
            cookTimeMinutes = "1",
            servings = "1",
            updatedAt = 1L,
        )

        val creator = User(UUID.randomUUID(), "Chef", "chef@example.com", "")
        val recipe = draft.toRecipe(creator)

        assertEquals("https://example.com", recipe.recipeExternalUrl)
    }

    @Test
    fun `draft toRecipe carries localImagePath through`() {
        val draft = RecipeDraft(
            recipeId = UUID.randomUUID(),
            isNewRecipe = true,
            localImagePath = "/files/recipe_images/abc",
            prepTimeMinutes = "1",
            cookTimeMinutes = "1",
            servings = "1",
            updatedAt = 1L,
        )

        val creator = User(UUID.randomUUID(), "Chef", "chef@example.com", "")
        val recipe = draft.toRecipe(creator)

        assertEquals("/files/recipe_images/abc", recipe.localImagePath)
    }

    @Test(expected = NumberFormatException::class)
    fun `draft toRecipe throws when prepTime is invalid`() {
        val draft = RecipeDraft(
            recipeId = UUID.randomUUID(),
            isNewRecipe = true,
            prepTimeMinutes = "abc",
            cookTimeMinutes = "1",
            servings = "1",
            updatedAt = 1L,
        )

        val creator = User(UUID.randomUUID(), "Chef", "chef@example.com", "")
        draft.toRecipe(creator) // should throw
    }
}
