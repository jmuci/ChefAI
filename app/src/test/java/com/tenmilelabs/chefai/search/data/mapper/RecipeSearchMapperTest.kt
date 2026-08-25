package com.tenmilelabs.chefai.search.data.mapper

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.search.data.network.dto.RecipeSearchLabelDto
import com.tenmilelabs.chefai.search.data.network.dto.RecipeSearchResultDto
import com.tenmilelabs.chefai.search.data.network.dto.RecipeSearchTagDto
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class RecipeSearchMapperTest {

    private fun dto(
        uuid: String = UUID.randomUUID().toString(),
        privacy: String = "PUBLIC",
        tags: List<RecipeSearchTagDto> = emptyList(),
        labels: List<RecipeSearchLabelDto> = emptyList(),
    ) = RecipeSearchResultDto(
        uuid = uuid,
        title = "Tomato Soup",
        description = "Warm and comforting",
        imageUrl = "https://example.com/full.jpg",
        imageUrlThumbnail = "https://example.com/thumb.jpg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 20,
        servings = 4,
        creatorId = UUID.randomUUID().toString(),
        privacy = privacy,
        updatedAt = 1_000L,
        tags = tags,
        labels = labels,
    )

    @Test
    fun `maps scalar fields straight across`() {
        val source = dto()

        val preview = source.toRecipePreview()

        assertThat(preview.uuid).isEqualTo(UUID.fromString(source.uuid))
        assertThat(preview.title).isEqualTo(source.title)
        assertThat(preview.description).isEqualTo(source.description)
        assertThat(preview.imageUrlThumbnail).isEqualTo(source.imageUrlThumbnail)
        assertThat(preview.prepTimeMinutes).isEqualTo(source.prepTimeMinutes)
        assertThat(preview.cookTimeMinutes).isEqualTo(source.cookTimeMinutes)
        assertThat(preview.servings).isEqualTo(source.servings)
        assertThat(preview.creatorId).isEqualTo(UUID.fromString(source.creatorId))
    }

    @Test
    fun `never carries a local image path — a search result was never cached on this device`() {
        val preview = dto().toRecipePreview()

        assertThat(preview.localImagePath).isNull()
    }

    @Test
    fun `maps tags and labels preserving id and display name`() {
        val tagId = UUID.randomUUID().toString()
        val labelId = UUID.randomUUID().toString()
        val preview = dto(
            tags = listOf(RecipeSearchTagDto(tagId, "Vegan")),
            labels = listOf(RecipeSearchLabelDto(labelId, "Quick")),
        ).toRecipePreview()

        assertThat(preview.tags).containsExactly(
            com.tenmilelabs.chefai.core.domain.model.Tag(UUID.fromString(tagId), "Vegan")
        )
        assertThat(preview.labels).containsExactly(
            com.tenmilelabs.chefai.core.domain.model.Label(UUID.fromString(labelId), "Quick")
        )
    }

    @Test
    fun `parses the privacy enum from the wire value`() {
        val preview = dto(privacy = "PRIVATE").toRecipePreview()

        assertThat(preview.privacy).isEqualTo(RecipePrivacy.PRIVATE)
    }

    @Test
    fun `throws on a malformed uuid rather than silently dropping the result`() {
        assertThrows(IllegalArgumentException::class.java) {
            dto(uuid = "not-a-uuid").toRecipePreview()
        }
    }
}
