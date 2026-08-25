package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GrocerySectionClassifierTest {

    @Test
    fun `whole-token matching keeps eggplant out of dairy`() {
        assertThat(GrocerySectionClassifier.classify("Eggplant")).isEqualTo(GrocerySection.PRODUCE)
    }

    @Test
    fun `eggs match dairy and eggs regardless of case or plural`() {
        assertThat(GrocerySectionClassifier.classify("Eggs")).isEqualTo(GrocerySection.DAIRY_AND_EGGS)
        assertThat(GrocerySectionClassifier.classify("egg")).isEqualTo(GrocerySection.DAIRY_AND_EGGS)
    }

    @Test
    fun `cornstarch is spices and baking, corn alone is produce`() {
        assertThat(GrocerySectionClassifier.classify("Cornstarch")).isEqualTo(GrocerySection.SPICES_AND_BAKING)
        assertThat(GrocerySectionClassifier.classify("Corn")).isEqualTo(GrocerySection.PRODUCE)
    }

    @Test
    fun `frozen override beats the produce token for peas`() {
        assertThat(GrocerySectionClassifier.classify("Frozen peas")).isEqualTo(GrocerySection.FROZEN)
    }

    @Test
    fun `canned override beats the pantry token for chickpeas`() {
        assertThat(GrocerySectionClassifier.classify("Canned chickpeas")).isEqualTo(GrocerySection.PANTRY)
    }

    @Test
    fun `coconut milk is pantry, almond milk and plain milk are dairy`() {
        assertThat(GrocerySectionClassifier.classify("Coconut milk")).isEqualTo(GrocerySection.PANTRY)
        assertThat(GrocerySectionClassifier.classify("Almond milk")).isEqualTo(GrocerySection.DAIRY_AND_EGGS)
        assertThat(GrocerySectionClassifier.classify("Milk")).isEqualTo(GrocerySection.DAIRY_AND_EGGS)
    }

    @Test
    fun `chicken stock is pantry, chicken breast is meat`() {
        assertThat(GrocerySectionClassifier.classify("Chicken stock")).isEqualTo(GrocerySection.PANTRY)
        assertThat(GrocerySectionClassifier.classify("Chicken breast")).isEqualTo(GrocerySection.MEAT_AND_SEAFOOD)
    }

    @Test
    fun `punctuation and case do not affect classification`() {
        assertThat(GrocerySectionClassifier.classify("Extra-Virgin Olive Oil (cold pressed)"))
            .isEqualTo(GrocerySection.PANTRY)
    }

    @Test
    fun `unrecognised ingredients fall back to other`() {
        assertThat(GrocerySectionClassifier.classify("Unobtainium dust")).isEqualTo(GrocerySection.OTHER)
    }

    @Test
    fun `blank input falls back to other`() {
        assertThat(GrocerySectionClassifier.classify("   ")).isEqualTo(GrocerySection.OTHER)
    }
}
