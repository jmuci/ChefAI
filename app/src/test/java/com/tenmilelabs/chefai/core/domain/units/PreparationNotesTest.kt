package com.tenmilelabs.chefai.core.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreparationNotesTest {

    @Test
    fun `a comma-separated note is dropped`() {
        assertThat(PreparationNotes.stripFrom("garlic, peeled and crushed")).isEqualTo("garlic")
        assertThat(PreparationNotes.stripFrom("onion, finely chopped")).isEqualTo("onion")
        assertThat(PreparationNotes.stripFrom("tuna in oil, drained")).isEqualTo("tuna in oil")
    }

    @Test
    fun `a note run on without a comma is dropped`() {
        assertThat(PreparationNotes.stripFrom("garlic peeled crushed")).isEqualTo("garlic")
        assertThat(PreparationNotes.stripFrom("cheese grated")).isEqualTo("cheese")
    }

    @Test
    fun `spanish notes inflect for gender and number`() {
        assertThat(PreparationNotes.stripFrom("ajo picado")).isEqualTo("ajo")
        assertThat(PreparationNotes.stripFrom("cebollas, picadas finamente")).isEqualTo("cebollas")
        assertThat(PreparationNotes.stripFrom("zanahoria cortada en rodajas")).isEqualTo("zanahoria")
        assertThat(PreparationNotes.stripFrom("ajo, pelado y machacado")).isEqualTo("ajo")
    }

    @Test
    fun `a leading preparation word is part of the product and stays`() {
        assertThat(PreparationNotes.stripFrom("diced tomatoes")).isEqualTo("diced tomatoes")
        assertThat(PreparationNotes.stripFrom("chopped frozen spinach")).isEqualTo("chopped frozen spinach")
    }

    @Test
    fun `ground is not a preparation note`() {
        assertThat(PreparationNotes.stripFrom("beef, ground")).isEqualTo("beef, ground")
        assertThat(PreparationNotes.stripFrom("carne molida")).isEqualTo("carne molida")
    }

    @Test
    fun `a name that is nothing but a note is left whole`() {
        assertThat(PreparationNotes.stripFrom("chopped")).isEqualTo("chopped")
    }

    @Test
    fun `connectors alone do not make a note`() {
        assertThat(PreparationNotes.stripFrom("salt, to taste")).isEqualTo("salt, to taste")
        assertThat(PreparationNotes.stripFrom("bread in slices")).isEqualTo("bread in slices")
    }
}
