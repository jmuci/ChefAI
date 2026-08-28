package com.tenmilelabs.chefai.recipes.data.mapper

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.User
import org.junit.Test

/**
 * Guards the image fields across the domain↔entity boundary.
 *
 * [toRoomEntity] builds a whole [com.tenmilelabs.chefai.core.data.local.room.RecipeEntity] and is fed
 * straight into a full-row `@Upsert`, so any column it forgets is silently reset on every save that
 * goes through the domain model. That is not hypothetical: the same shape of bug wiped a freshly
 * cached `localImagePath` on the sync path (#139), and `deletedAt` is hardcoded to null here today
 * (#128). These tests pin the two image pointers so a third instance can't land quietly.
 */
class RoomDomainMapTest {

    private val creator = User(
        uuid = UuidV7Generator.newId(),
        displayName = "Tester",
        email = "tester@example.com",
        avatarUrl = "",
    )

    private fun recipe(
        localImagePath: String? = null,
        imageBlobId: String? = null,
        caloriesPerServing: Int? = null,
        proteinGramsPerServing: Int? = null,
    ) = Recipe(
        uuid = UuidV7Generator.newId(),
        title = "Carbonara",
        description = "Classic",
        imageUrl = "https://example.com/x.jpg",
        imageUrlThumbnail = "https://example.com/x.jpg",
        localImagePath = localImagePath,
        imageBlobId = imageBlobId,
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 2,
        caloriesPerServing = caloriesPerServing,
        proteinGramsPerServing = proteinGramsPerServing,
        creator = creator,
        recipeExternalUrl = null,
        ingredients = emptyList(),
        steps = emptyList(),
        tags = emptyList(),
        labels = emptyList(),
        updatedAt = 555L,
    )

    @Test
    fun `toRoomEntity carries imageBlobId, so saving does not orphan an uploaded image`() {
        val entity = recipe(imageBlobId = "abc123").toRoomEntity()

        assertThat(entity.imageBlobId).isEqualTo("abc123")
    }

    @Test
    fun `toRoomEntity carries localImagePath`() {
        val entity = recipe(localImagePath = "/files/recipe_images/x").toRoomEntity()

        assertThat(entity.localImagePath).isEqualTo("/files/recipe_images/x")
    }

    @Test
    fun `a recipe with neither pointer maps to nulls, not empty strings`() {
        // recipeImageModel treats "" and null differently — Coil throws on an empty model.
        val entity = recipe().toRoomEntity()

        assertThat(entity.imageBlobId).isNull()
        assertThat(entity.localImagePath).isNull()
    }

    @Test
    fun `toRoomEntity carries calories and protein per serving`() {
        val entity = recipe(caloriesPerServing = 350, proteinGramsPerServing = 12).toRoomEntity()

        assertThat(entity.caloriesPerServing).isEqualTo(350)
        assertThat(entity.proteinGramsPerServing).isEqualTo(12)
    }

    @Test
    fun `a recipe with no nutrition data maps to null, not zero`() {
        val entity = recipe().toRoomEntity()

        assertThat(entity.caloriesPerServing).isNull()
        assertThat(entity.proteinGramsPerServing).isNull()
    }
}
