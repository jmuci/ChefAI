package com.tenmilelabs.chefai.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeLanguageTest {

    @Test
    fun `fromCode finds an offered language by its ISO code`() {
        assertEquals(RecipeLanguage.SPANISH, RecipeLanguage.fromCode("es"))
        assertEquals(RecipeLanguage.GERMAN, RecipeLanguage.fromCode("de"))
    }

    @Test
    fun `fromCode returns null for a code the app doesn't offer`() {
        assertNull(RecipeLanguage.fromCode("nl"))
        assertNull(RecipeLanguage.fromCode(null))
        assertNull(RecipeLanguage.fromCode("ES"))
    }

    @Test
    fun `forDeviceLanguage starts from the device language when offered, else English`() {
        assertEquals(RecipeLanguage.SPANISH, RecipeLanguage.forDeviceLanguage("es"))
        assertEquals(RecipeLanguage.ENGLISH, RecipeLanguage.forDeviceLanguage("nl"))
    }
}
