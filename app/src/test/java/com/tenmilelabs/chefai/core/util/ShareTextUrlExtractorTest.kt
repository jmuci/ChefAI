package com.tenmilelabs.chefai.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShareTextUrlExtractorTest {

    @Test
    fun `extracts a bare url`() {
        assertThat(extractSharedRecipeUrl("https://example.com/recipe")).isEqualTo("https://example.com/recipe")
    }

    @Test
    fun `extracts a url embedded in prose`() {
        val shared = "Check out this recipe! https://example.com/recipe/143 So good."
        assertThat(extractSharedRecipeUrl(shared)).isEqualTo("https://example.com/recipe/143")
    }

    @Test
    fun `strips trailing sentence punctuation`() {
        assertThat(extractSharedRecipeUrl("Try this: https://example.com/recipe."))
            .isEqualTo("https://example.com/recipe")
        assertThat(extractSharedRecipeUrl("(https://example.com/recipe)"))
            .isEqualTo("https://example.com/recipe")
    }

    @Test
    fun `returns null when there is no url`() {
        assertThat(extractSharedRecipeUrl("Just a note about dinner, no link here.")).isNull()
    }

    @Test
    fun `returns null for null or blank input`() {
        assertThat(extractSharedRecipeUrl(null)).isNull()
        assertThat(extractSharedRecipeUrl("")).isNull()
    }

    @Test
    fun `returns null for a private-network url, same as manual paste`() {
        assertThat(extractSharedRecipeUrl("check this out http://192.168.1.5/recipe")).isNull()
        assertThat(extractSharedRecipeUrl("http://localhost:8080/recipe")).isNull()
    }

    @Test
    fun `ignores a non-http scheme`() {
        assertThat(extractSharedRecipeUrl("ftp://example.com/recipe")).isNull()
    }

    @Test
    fun `picks the first url when multiple are present`() {
        val shared = "https://example.com/first and also https://example.com/second"
        assertThat(extractSharedRecipeUrl(shared)).isEqualTo("https://example.com/first")
    }
}
