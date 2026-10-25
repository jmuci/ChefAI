package com.tenmilelabs.chefai.recipes.ui

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.testutil.recipePreview1
import org.junit.Test
import java.util.UUID

class RecipeFilterTest {

    private val plain = recipePreview1.copy(tags = emptyList(), labels = emptyList())

    @Test
    fun `ALL matches everything`() {
        assertThat(RecipeFilter.ALL.matches(plain)).isTrue()
    }

    @Test
    fun `QUICK_EASY matches when prep plus cook is at most the threshold`() {
        // Given: total exactly at the threshold, and one minute over
        val atLimit = plain.copy(prepTimeMinutes = 5, cookTimeMinutes = QUICK_EASY_MAX_TOTAL_MINUTES - 5)
        val overLimit = plain.copy(prepTimeMinutes = 5, cookTimeMinutes = QUICK_EASY_MAX_TOTAL_MINUTES - 4)

        // Then
        assertThat(RecipeFilter.QUICK_EASY.matches(atLimit)).isTrue()
        assertThat(RecipeFilter.QUICK_EASY.matches(overLimit)).isFalse()
    }

    @Test
    fun `QUICK_EASY excludes recipes with no time recorded`() {
        val unknown = plain.copy(prepTimeMinutes = 0, cookTimeMinutes = 0)

        assertThat(RecipeFilter.QUICK_EASY.matches(unknown)).isFalse()
    }

    @Test
    fun `DESSERT matches a tag case-insensitively`() {
        val recipe = plain.copy(tags = listOf(Tag(UUID.randomUUID(), "Desserts")))

        assertThat(RecipeFilter.DESSERT.matches(recipe)).isTrue()
    }

    @Test
    fun `DESSERT matches a label`() {
        val recipe = plain.copy(labels = listOf(Label(UUID.randomUUID(), "dessert")))

        assertThat(RecipeFilter.DESSERT.matches(recipe)).isTrue()
    }

    @Test
    fun `DESSERT does not match unrelated tags or labels`() {
        val recipe = plain.copy(
            tags = listOf(Tag(UUID.randomUUID(), "Baking")),
            labels = listOf(Label(UUID.randomUUID(), "Dinner")),
        )

        assertThat(RecipeFilter.DESSERT.matches(recipe)).isFalse()
    }
}
