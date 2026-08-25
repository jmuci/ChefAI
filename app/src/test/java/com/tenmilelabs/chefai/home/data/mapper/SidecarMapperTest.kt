package com.tenmilelabs.chefai.home.data.mapper

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.home.data.model.SidecarCreatorDto
import com.tenmilelabs.chefai.home.data.model.SidecarIngredient
import com.tenmilelabs.chefai.home.data.model.SidecarLabelDto
import com.tenmilelabs.chefai.home.data.model.SidecarRecipeDto
import com.tenmilelabs.chefai.home.data.model.SidecarStep
import com.tenmilelabs.chefai.home.data.model.SidecarTagDto
import org.junit.Test
import java.util.UUID

class SidecarMapperTest {

    private fun recipeDto(privacy: String = "PUBLIC") = SidecarRecipeDto(
        uuid = UUID.randomUUID().toString(),
        title = "Pancakes",
        description = "Fluffy",
        imageUrl = "https://example.com/full.jpg",
        imageUrlThumbnail = "https://example.com/thumb.jpg",
        prepTimeMinutes = 5,
        cookTimeMinutes = 15,
        servings = 2,
        creatorId = UUID.randomUUID().toString(),
        privacy = privacy,
        updatedAt = 42L,
    )

    @Test
    fun `recipe entity is always marked SYNCED and carries no external url`() {
        val entity = recipeDto().toRecipeEntity()

        assertThat(entity.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(entity.recipeExternalUrl).isNull()
        assertThat(entity.deletedAt).isNull()
        assertThat(entity.version).isEqualTo(1)
    }

    @Test
    fun `recipe privacy is parsed case-insensitively`() {
        assertThat(recipeDto(privacy = "private").toRecipeEntity().privacy)
            .isEqualTo(RecipePrivacy.PRIVATE)
        assertThat(recipeDto(privacy = "PUBLIC").toRecipeEntity().privacy)
            .isEqualTo(RecipePrivacy.PUBLIC)
    }

    @Test
    fun `tag and label entities are marked SYNCED with a zero updatedAt sentinel`() {
        val tagUuid = UUID.randomUUID().toString()
        val labelUuid = UUID.randomUUID().toString()

        val tagEntity = SidecarTagDto(tagUuid, "Vegan").toTagEntity()
        val labelEntity = SidecarLabelDto(labelUuid, "Quick").toLabelEntity()

        assertThat(tagEntity.uuid).isEqualTo(UUID.fromString(tagUuid))
        assertThat(tagEntity.displayName).isEqualTo("Vegan")
        assertThat(tagEntity.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(labelEntity.uuid).isEqualTo(UUID.fromString(labelUuid))
        assertThat(labelEntity.displayName).isEqualTo("Quick")
    }

    @Test
    fun `creator entity defaults a missing avatar url to blank rather than staying null`() {
        val dto = SidecarCreatorDto(UUID.randomUUID().toString(), "Chef", avatarUrl = null)

        val entity = dto.toUserEntity()

        assertThat(entity.avatarUrl).isEmpty()
        assertThat(entity.email).isEmpty()
    }

    @Test
    fun `ingredient id is derived deterministically from the normalized name`() {
        val exact = SidecarIngredient(name = "Olive Oil", quantity = 1.0, unit = "tbsp")
        val padded = SidecarIngredient(name = "  olive oil  ", quantity = 2.0, unit = "cup")

        val exactEntity = exact.toIngredientEntity(now = 10L)
        val paddedEntity = padded.toIngredientEntity(now = 20L)

        assertThat(exactEntity.uuid).isEqualTo(paddedEntity.uuid)
        assertThat(exactEntity.displayName).isEqualTo("Olive Oil")
    }

    @Test
    fun `a different ingredient name derives a different id`() {
        val a = SidecarIngredient(name = "Salt", quantity = 1.0, unit = "tsp").toIngredientEntity(0L)
        val b = SidecarIngredient(name = "Pepper", quantity = 1.0, unit = "tsp").toIngredientEntity(0L)

        assertThat(a.uuid).isNotEqualTo(b.uuid)
    }

    @Test
    fun `recipe ingredient entity shares its ingredient id with the standalone ingredient entity`() {
        val recipeId = UUID.randomUUID()
        val ingredient = SidecarIngredient(name = "Flour", quantity = 2.0, unit = "cup")

        val ingredientEntity = ingredient.toIngredientEntity(now = 5L)
        val recipeIngredientEntity = ingredient.toRecipeIngredientEntity(recipeId, now = 5L)

        assertThat(recipeIngredientEntity.ingredientId).isEqualTo(ingredientEntity.uuid)
        assertThat(recipeIngredientEntity.recipeId).isEqualTo(recipeId)
        assertThat(recipeIngredientEntity.quantity).isEqualTo(2.0)
        assertThat(recipeIngredientEntity.unit).isEqualTo("cup")
    }

    @Test
    fun `step id is derived from recipe id and order index, so reordering yields a new id`() {
        val recipeId = UUID.randomUUID()
        val step = SidecarStep(orderIndex = 2, instruction = "Whisk")

        val first = step.toRecipeStepEntity(recipeId, now = 1L)
        val reordered = step.copy(orderIndex = 3).toRecipeStepEntity(recipeId, now = 1L)

        assertThat(first.uuid).isNotEqualTo(reordered.uuid)
        assertThat(first.recipeId).isEqualTo(recipeId)
        assertThat(first.orderIndex).isEqualTo(2)
        assertThat(first.instruction).isEqualTo("Whisk")
    }

    @Test
    fun `the same recipe id and order index always derive the same step id`() {
        val recipeId = UUID.randomUUID()
        val step = SidecarStep(orderIndex = 0, instruction = "Preheat")

        val a = step.toRecipeStepEntity(recipeId, now = 1L)
        val b = step.toRecipeStepEntity(recipeId, now = 999L)

        assertThat(a.uuid).isEqualTo(b.uuid)
    }
}
