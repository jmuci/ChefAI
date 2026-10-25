package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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

    @Test
    fun `egg written as its own word inside another ingredient is not dairy`() {
        // "Egg Plants" splits into the tokens "egg" and "plants"; it is still an eggplant
        assertThat(GrocerySectionClassifier.classify("Egg Plants")).isEqualTo(GrocerySection.PRODUCE)
        assertThat(GrocerySectionClassifier.classify("egg plant")).isEqualTo(GrocerySection.PRODUCE)
        assertThat(GrocerySectionClassifier.classify("Egg noodles")).isEqualTo(GrocerySection.PANTRY)
        assertThat(GrocerySectionClassifier.classify("Egg yolks")).isEqualTo(GrocerySection.DAIRY_AND_EGGS)
        assertThat(GrocerySectionClassifier.classify("Nutmeg")).isEqualTo(GrocerySection.SPICES_AND_BAKING)
    }

    @Test
    fun `phrases match whole words, with a plural on the last one`() {
        // "martin of" must not read as "tin of"; "chicken breasts" still hits "chicken breast"
        assertThat(GrocerySectionClassifier.classify("Martin of the farm cheese")).isEqualTo(GrocerySection.DAIRY_AND_EGGS)
        assertThat(GrocerySectionClassifier.classify("Spring onions")).isEqualTo(GrocerySection.PRODUCE)
    }

    @Test
    fun `the imported Spanish recipe's staples land in their aisles`() {
        val expected = mapOf(
            "huevos M" to GrocerySection.DAIRY_AND_EGGS,
            "cebolla pequeña" to GrocerySection.PRODUCE,
            "ajo" to GrocerySection.PRODUCE,
            "patatas especiales para freír" to GrocerySection.PRODUCE,
            "setas Boletus frescos" to GrocerySection.PRODUCE,
            "Perejil fresco" to GrocerySection.PRODUCE,
            "Aceite de oliva virgen extra" to GrocerySection.PANTRY,
            "Sal y pimienta negra recién molida" to GrocerySection.SPICES_AND_BAKING,
        )
        expected.forEach { (name, section) ->
            assertWithMessage(name).that(GrocerySectionClassifier.classify(name)).isEqualTo(section)
        }
    }

    @Test
    fun `Spanish keywords cover accents, plurals and the usual traps`() {
        assertThat(GrocerySectionClassifier.classify("piña")).isEqualTo(GrocerySection.PRODUCE)
        assertThat(GrocerySectionClassifier.classify("Jamón serrano")).isEqualTo(GrocerySection.MEAT_AND_SEAFOOD)
        assertThat(GrocerySectionClassifier.classify("limones")).isEqualTo(GrocerySection.PRODUCE)
        assertThat(GrocerySectionClassifier.classify("champiñones")).isEqualTo(GrocerySection.PRODUCE)
        assertThat(GrocerySectionClassifier.classify("caldo de pollo")).isEqualTo(GrocerySection.PANTRY)
        assertThat(GrocerySectionClassifier.classify("nuez moscada")).isEqualTo(GrocerySection.SPICES_AND_BAKING)
        assertThat(GrocerySectionClassifier.classify("pan rallado")).isEqualTo(GrocerySection.SPICES_AND_BAKING)
        assertThat(GrocerySectionClassifier.classify("pimiento rojo")).isEqualTo(GrocerySection.PRODUCE)
        assertThat(GrocerySectionClassifier.classify("leche entera")).isEqualTo(GrocerySection.DAIRY_AND_EGGS)
        assertThat(GrocerySectionClassifier.classify("guisantes congelados")).isEqualTo(GrocerySection.FROZEN)
    }
}
