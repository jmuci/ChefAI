package com.tenmilelabs.chefai.home.ui.components

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import org.junit.Test

class SduiCarouselTest {

    private fun listCard(id: String) = ComponentModel.ListCard(id = id, recipeId = "r-$id")
    private fun squaredCard(id: String) = ComponentModel.SquaredCard(id = id, recipeId = "r-$id")

    @Test
    fun `carousel of only list cards renders as a list`() {
        // Given
        val carousel = ComponentModel.Carousel(id = "c", items = listOf(listCard("a"), listCard("b")))

        // When / Then
        assertThat(carousel.rendersAsList).isTrue()
    }

    @Test
    fun `carousel mixing list cards with other cards scrolls horizontally`() {
        // Given
        val carousel = ComponentModel.Carousel(id = "c", items = listOf(listCard("a"), squaredCard("b")))

        // When / Then
        assertThat(carousel.rendersAsList).isFalse()
    }

    @Test
    fun `carousel without list cards scrolls horizontally`() {
        // Given
        val carousel = ComponentModel.Carousel(id = "c", items = listOf(squaredCard("a")))

        // When / Then
        assertThat(carousel.rendersAsList).isFalse()
    }

    @Test
    fun `empty carousel is not a list`() {
        // Given
        val carousel = ComponentModel.Carousel(id = "c")

        // When / Then
        assertThat(carousel.rendersAsList).isFalse()
    }
}
