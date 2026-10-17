package com.tenmilelabs.chefai.home.ui

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import org.junit.Test

class HomeSectionsTest {

    private fun header(id: String) = ComponentModel.SectionHeader(id = id, title = id)
    private fun carousel(id: String) = ComponentModel.Carousel(id = id)
    private fun card(id: String) = ComponentModel.LargeCard(id = id, recipeId = "r-$id")

    @Test
    fun `empty list yields no sections`() {
        assertThat(groupIntoSections(emptyList())).isEmpty()
    }

    @Test
    fun `each header opens a section holding the components that follow it`() {
        // Given
        val components = listOf(
            header("for-you"), carousel("c1"),
            header("italian"), carousel("c2"), card("extra"),
        )

        // When
        val sections = groupIntoSections(components)

        // Then
        assertThat(sections).containsExactly(
            HomeSection(key = "for-you", components = listOf(header("for-you"), carousel("c1"))),
            HomeSection(
                key = "italian",
                components = listOf(header("italian"), carousel("c2"), card("extra")),
            ),
        ).inOrder()
    }

    @Test
    fun `components before the first header form a header-less section`() {
        // Given — a hero card leading the layout
        val components = listOf(card("tonight"), header("for-you"), carousel("c1"))

        // When
        val sections = groupIntoSections(components)

        // Then
        assertThat(sections.map { it.key }).containsExactly("tonight", "for-you").inOrder()
        assertThat(sections.first().components).containsExactly(card("tonight"))
    }

    @Test
    fun `consecutive headers each get their own section`() {
        // Given
        val components = listOf(header("a"), header("b"), carousel("c"))

        // When
        val sections = groupIntoSections(components)

        // Then
        assertThat(sections.map { section -> section.components.map { it.id } })
            .containsExactly(listOf("a"), listOf("b", "c"))
            .inOrder()
    }
}
