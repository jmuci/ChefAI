package com.tenmilelabs.chefai.core.data.sync.mapper

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeIngredientDto
import com.tenmilelabs.chefai.core.data.sync.network.dto.SyncRecipeStepDto
import org.junit.Test
import java.util.UUID

class SyncMapperTest {

    // --- Test data ---
    private val recipeId = UuidV7Generator.newId()
    private val creatorId = UuidV7Generator.newId()
    private val ingredientId1 = UuidV7Generator.newId()
    private val ingredientId2 = UuidV7Generator.newId()
    private val stepId1 = UuidV7Generator.newId()
    private val stepId2 = UuidV7Generator.newId()
    private val tagId1 = UuidV7Generator.newId()
    private val labelId1 = UuidV7Generator.newId()
    private val updatedAt = 1000000L

    private val recipeEntity = RecipeEntity(
        uuid = recipeId,
        title = "Test Recipe",
        description = "A test recipe description",
        imageUrl = "https://example.com/image.jpg",
        imageUrlThumbnail = "https://example.com/thumb.jpg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 4,
        creatorId = creatorId,
        recipeExternalUrl = "https://example.com/recipe",
        privacy = RecipePrivacy.PUBLIC,
        updatedAt = updatedAt,
        deletedAt = null,
        syncState = SyncState.PENDING
    )

    private val stepEntities = listOf(
        RecipeStepEntity(
            uuid = stepId1,
            recipeId = recipeId,
            orderIndex = 0,
            instruction = "Step 1: Mix ingredients",
            updatedAt = updatedAt,
            deletedAt = null
        ),
        RecipeStepEntity(
            uuid = stepId2,
            recipeId = recipeId,
            orderIndex = 1,
            instruction = "Step 2: Cook",
            updatedAt = updatedAt,
            deletedAt = null
        )
    )

    private val ingredientEntities = listOf(
        RecipeIngredientEntity(
            recipeId = recipeId,
            ingredientId = ingredientId1,
            quantity = 200.0,
            unit = "grams",
            updatedAt = updatedAt
        ),
        RecipeIngredientEntity(
            recipeId = recipeId,
            ingredientId = ingredientId2,
            quantity = 3.0,
            unit = "cups",
            updatedAt = updatedAt
        )
    )

    private val tagCrossRefs = listOf(
        RecipeTagCrossRef(
            recipeId = recipeId,
            tagId = tagId1,
            updatedAt = updatedAt,
            deletedAt = null
        )
    )

    private val labelCrossRefs = listOf(
        RecipeLabelCrossRef(
            recipeId = recipeId,
            labelId = labelId1,
            updatedAt = updatedAt,
            deletedAt = null
        )
    )

    // --- Push direction tests (Entity -> DTO) ---

    @Test
    fun `toSyncDto maps RecipeEntity fields correctly`() {
        val dto = recipeEntity.toSyncDto(stepEntities, ingredientEntities, tagCrossRefs, labelCrossRefs)

        assertThat(dto.uuid).isEqualTo(recipeId.toString())
        assertThat(dto.title).isEqualTo("Test Recipe")
        assertThat(dto.description).isEqualTo("A test recipe description")
        assertThat(dto.imageUrl).isEqualTo("https://example.com/image.jpg")
        assertThat(dto.imageUrlThumbnail).isEqualTo("https://example.com/thumb.jpg")
        assertThat(dto.prepTimeMinutes).isEqualTo(10)
        assertThat(dto.cookTimeMinutes).isEqualTo(20)
        assertThat(dto.servings).isEqualTo(4)
        assertThat(dto.creatorId).isEqualTo(creatorId.toString())
        assertThat(dto.recipeExternalUrl).isEqualTo("https://example.com/recipe")
        assertThat(dto.privacy).isEqualTo("PUBLIC")
        assertThat(dto.updatedAt).isEqualTo(updatedAt)
        assertThat(dto.deletedAt).isNull()
    }

    @Test
    fun `toSyncDto maps steps correctly`() {
        val dto = recipeEntity.toSyncDto(stepEntities, ingredientEntities, tagCrossRefs, labelCrossRefs)

        assertThat(dto.steps).hasSize(2)
        assertThat(dto.steps[0].uuid).isEqualTo(stepId1.toString())
        assertThat(dto.steps[0].orderIndex).isEqualTo(0)
        assertThat(dto.steps[0].instruction).isEqualTo("Step 1: Mix ingredients")
        assertThat(dto.steps[1].uuid).isEqualTo(stepId2.toString())
        assertThat(dto.steps[1].orderIndex).isEqualTo(1)
    }

    @Test
    fun `toSyncDto maps ingredients correctly`() {
        val dto = recipeEntity.toSyncDto(stepEntities, ingredientEntities, tagCrossRefs, labelCrossRefs)

        assertThat(dto.ingredients).hasSize(2)
        assertThat(dto.ingredients[0].ingredientId).isEqualTo(ingredientId1.toString())
        assertThat(dto.ingredients[0].quantity).isEqualTo(200.0)
        assertThat(dto.ingredients[0].unit).isEqualTo("grams")
        assertThat(dto.ingredients[1].ingredientId).isEqualTo(ingredientId2.toString())
    }

    @Test
    fun `toSyncDto maps tag and label IDs as strings`() {
        val dto = recipeEntity.toSyncDto(stepEntities, ingredientEntities, tagCrossRefs, labelCrossRefs)

        assertThat(dto.tagIds).containsExactly(tagId1.toString())
        assertThat(dto.labelIds).containsExactly(labelId1.toString())
    }

    @Test
    fun `toSyncDto converts UUID to string format`() {
        val dto = recipeEntity.toSyncDto(stepEntities, ingredientEntities, tagCrossRefs, labelCrossRefs)

        // Verify all IDs are valid UUID strings
        assertThat(UUID.fromString(dto.uuid)).isEqualTo(recipeId)
        assertThat(UUID.fromString(dto.creatorId)).isEqualTo(creatorId)
        dto.steps.forEach { step -> assertThat(UUID.fromString(step.uuid)).isNotNull() }
        dto.ingredients.forEach { ing -> assertThat(UUID.fromString(ing.ingredientId)).isNotNull() }
    }

    @Test
    fun `toSyncDto with empty children produces empty lists`() {
        val dto = recipeEntity.toSyncDto(emptyList(), emptyList(), emptyList(), emptyList())

        assertThat(dto.steps).isEmpty()
        assertThat(dto.ingredients).isEmpty()
        assertThat(dto.tagIds).isEmpty()
        assertThat(dto.labelIds).isEmpty()
    }

    // --- Pull direction tests (DTO -> Entity) ---

    private val syncRecipeDto = SyncRecipeDto(
        uuid = recipeId.toString(),
        title = "Server Recipe",
        description = "From server",
        imageUrl = "https://server.com/img.jpg",
        imageUrlThumbnail = "https://server.com/thumb.jpg",
        prepTimeMinutes = 15,
        cookTimeMinutes = 30,
        servings = 2,
        creatorId = creatorId.toString(),
        recipeExternalUrl = null,
        privacy = "PRIVATE",
        updatedAt = 2000000L,
        deletedAt = null,
        steps = listOf(
            SyncRecipeStepDto(uuid = stepId1.toString(), orderIndex = 0, instruction = "Server step 1"),
            SyncRecipeStepDto(uuid = stepId2.toString(), orderIndex = 1, instruction = "Server step 2")
        ),
        ingredients = listOf(
            SyncRecipeIngredientDto(ingredientId = ingredientId1.toString(), quantity = 100.0, unit = "ml")
        ),
        tagIds = listOf(tagId1.toString()),
        labelIds = listOf(labelId1.toString())
    )

    @Test
    fun `toRecipeEntity maps DTO fields correctly and sets syncState to SYNCED`() {
        val entity = syncRecipeDto.toRecipeEntity()

        assertThat(entity.uuid).isEqualTo(recipeId)
        assertThat(entity.title).isEqualTo("Server Recipe")
        assertThat(entity.description).isEqualTo("From server")
        assertThat(entity.imageUrl).isEqualTo("https://server.com/img.jpg")
        assertThat(entity.imageUrlThumbnail).isEqualTo("https://server.com/thumb.jpg")
        assertThat(entity.prepTimeMinutes).isEqualTo(15)
        assertThat(entity.cookTimeMinutes).isEqualTo(30)
        assertThat(entity.servings).isEqualTo(2)
        assertThat(entity.creatorId).isEqualTo(creatorId)
        assertThat(entity.recipeExternalUrl).isNull()
        assertThat(entity.privacy).isEqualTo(RecipePrivacy.PRIVATE)
        assertThat(entity.updatedAt).isEqualTo(2000000L)
        assertThat(entity.deletedAt).isNull()
        assertThat(entity.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `toStepEntities maps steps with SYNCED syncState`() {
        val steps = syncRecipeDto.toStepEntities()

        assertThat(steps).hasSize(2)
        assertThat(steps[0].uuid).isEqualTo(stepId1)
        assertThat(steps[0].recipeId).isEqualTo(recipeId)
        assertThat(steps[0].orderIndex).isEqualTo(0)
        assertThat(steps[0].instruction).isEqualTo("Server step 1")
        assertThat(steps[0].updatedAt).isEqualTo(2000000L)
        assertThat(steps[0].deletedAt).isNull()
        assertThat(steps[0].syncState).isEqualTo(SyncState.SYNCED)

        assertThat(steps[1].uuid).isEqualTo(stepId2)
        assertThat(steps[1].orderIndex).isEqualTo(1)
        assertThat(steps[1].syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `toIngredientEntities maps ingredients with SYNCED syncState`() {
        val ingredients = syncRecipeDto.toIngredientEntities()

        assertThat(ingredients).hasSize(1)
        assertThat(ingredients[0].recipeId).isEqualTo(recipeId)
        assertThat(ingredients[0].ingredientId).isEqualTo(ingredientId1)
        assertThat(ingredients[0].quantity).isEqualTo(100.0)
        assertThat(ingredients[0].unit).isEqualTo("ml")
        assertThat(ingredients[0].updatedAt).isEqualTo(2000000L)
        assertThat(ingredients[0].deletedAt).isNull()
        assertThat(ingredients[0].syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `toTagCrossRefs maps tag IDs with SYNCED syncState`() {
        val tags = syncRecipeDto.toTagCrossRefs()

        assertThat(tags).hasSize(1)
        assertThat(tags[0].recipeId).isEqualTo(recipeId)
        assertThat(tags[0].tagId).isEqualTo(tagId1)
        assertThat(tags[0].updatedAt).isEqualTo(2000000L)
        assertThat(tags[0].deletedAt).isNull()
        assertThat(tags[0].syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `toLabelCrossRefs maps label IDs with SYNCED syncState`() {
        val labels = syncRecipeDto.toLabelCrossRefs()

        assertThat(labels).hasSize(1)
        assertThat(labels[0].recipeId).isEqualTo(recipeId)
        assertThat(labels[0].labelId).isEqualTo(labelId1)
        assertThat(labels[0].updatedAt).isEqualTo(2000000L)
        assertThat(labels[0].deletedAt).isNull()
        assertThat(labels[0].syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `round-trip preserves recipe data`() {
        // Entity -> DTO -> Entity
        val dto = recipeEntity.toSyncDto(stepEntities, ingredientEntities, tagCrossRefs, labelCrossRefs)
        val roundTrippedEntity = dto.toRecipeEntity()

        assertThat(roundTrippedEntity.uuid).isEqualTo(recipeEntity.uuid)
        assertThat(roundTrippedEntity.title).isEqualTo(recipeEntity.title)
        assertThat(roundTrippedEntity.description).isEqualTo(recipeEntity.description)
        assertThat(roundTrippedEntity.imageUrl).isEqualTo(recipeEntity.imageUrl)
        assertThat(roundTrippedEntity.prepTimeMinutes).isEqualTo(recipeEntity.prepTimeMinutes)
        assertThat(roundTrippedEntity.cookTimeMinutes).isEqualTo(recipeEntity.cookTimeMinutes)
        assertThat(roundTrippedEntity.servings).isEqualTo(recipeEntity.servings)
        assertThat(roundTrippedEntity.creatorId).isEqualTo(recipeEntity.creatorId)
        assertThat(roundTrippedEntity.privacy).isEqualTo(recipeEntity.privacy)
        // syncState changes to SYNCED on pull
        assertThat(roundTrippedEntity.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `round-trip preserves step data`() {
        val dto = recipeEntity.toSyncDto(stepEntities, ingredientEntities, tagCrossRefs, labelCrossRefs)
        val roundTrippedSteps = dto.toStepEntities()

        assertThat(roundTrippedSteps).hasSize(stepEntities.size)
        roundTrippedSteps.forEachIndexed { i, step ->
            assertThat(step.uuid).isEqualTo(stepEntities[i].uuid)
            assertThat(step.recipeId).isEqualTo(stepEntities[i].recipeId)
            assertThat(step.orderIndex).isEqualTo(stepEntities[i].orderIndex)
            assertThat(step.instruction).isEqualTo(stepEntities[i].instruction)
            assertThat(step.syncState).isEqualTo(SyncState.SYNCED)
        }
    }

    @Test
    fun `round-trip preserves ingredient data`() {
        val dto = recipeEntity.toSyncDto(stepEntities, ingredientEntities, tagCrossRefs, labelCrossRefs)
        val roundTrippedIngredients = dto.toIngredientEntities()

        assertThat(roundTrippedIngredients).hasSize(ingredientEntities.size)
        roundTrippedIngredients.forEachIndexed { i, ing ->
            assertThat(ing.recipeId).isEqualTo(ingredientEntities[i].recipeId)
            assertThat(ing.ingredientId).isEqualTo(ingredientEntities[i].ingredientId)
            assertThat(ing.quantity).isEqualTo(ingredientEntities[i].quantity)
            assertThat(ing.unit).isEqualTo(ingredientEntities[i].unit)
            assertThat(ing.syncState).isEqualTo(SyncState.SYNCED)
        }
    }

    @Test
    fun `toRecipeEntity with deletedAt preserves the value`() {
        val deletedDto = syncRecipeDto.copy(deletedAt = 3000000L)
        val entity = deletedDto.toRecipeEntity()

        assertThat(entity.deletedAt).isEqualTo(3000000L)
    }

    @Test
    fun `privacy enum round-trip for all values`() {
        RecipePrivacy.entries.forEach { privacy ->
            val entityWithPrivacy = recipeEntity.copy(privacy = privacy)
            val dto = entityWithPrivacy.toSyncDto(emptyList(), emptyList(), emptyList(), emptyList())
            val roundTripped = dto.toRecipeEntity()

            assertThat(dto.privacy).isEqualTo(privacy.name)
            assertThat(roundTripped.privacy).isEqualTo(privacy)
        }
    }
}
