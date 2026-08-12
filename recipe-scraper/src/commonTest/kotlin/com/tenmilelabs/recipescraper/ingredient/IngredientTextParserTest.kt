package com.tenmilelabs.recipescraper.ingredient

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IngredientTextParserTest {

    private fun assertParsed(
        raw: String,
        quantity: Double?,
        unit: String?,
        name: String,
    ) {
        val result = parseIngredient(raw)
        assertEquals(quantity, result.quantity, "quantity for \"$raw\"")
        assertEquals(unit, result.unit, "unit for \"$raw\"")
        assertEquals(name, result.name, "name for \"$raw\"")
        assertEquals(raw, result.raw, "raw must be preserved verbatim")
    }

    // --- Whole numbers and units ---

    @Test
    fun `parses whole number with plural unit`() {
        assertParsed("2 cups all-purpose flour, sifted", 2.0, "cup", "all-purpose flour, sifted")
    }

    @Test
    fun `normalizes unit synonyms to a canonical form`() {
        assertParsed("3 tablespoons butter", 3.0, "tbsp", "butter")
        assertParsed("3 tbsps butter", 3.0, "tbsp", "butter")
        assertParsed("2 teaspoons vanilla", 2.0, "tsp", "vanilla")
        assertParsed("500 grams beef", 500.0, "g", "beef")
        assertParsed("2 lbs potatoes", 2.0, "lb", "potatoes")
    }

    @Test
    fun `tolerates a trailing period on an abbreviation`() {
        assertParsed("2 tbsp. olive oil", 2.0, "tbsp", "olive oil")
    }

    @Test
    fun `distinguishes tablespoon and teaspoon by case`() {
        assertParsed("1 T sugar", 1.0, "tbsp", "sugar")
        assertParsed("1 t sugar", 1.0, "tsp", "sugar")
    }

    // --- Fractions ---

    @Test
    fun `parses unicode vulgar fraction fused to a whole number`() {
        assertParsed("1½ tbsp olive oil", 1.5, "tbsp", "olive oil")
    }

    @Test
    fun `parses a bare unicode vulgar fraction`() {
        assertParsed("½ tsp salt", 0.5, "tsp", "salt")
        assertParsed("¾ cup milk", 0.75, "cup", "milk")
    }

    @Test
    fun `parses an ascii mixed number across two tokens`() {
        assertParsed("1 1/2 cups sugar", 1.5, "cup", "sugar")
    }

    @Test
    fun `parses a bare ascii fraction`() {
        assertParsed("1/4 cup water", 0.25, "cup", "water")
    }

    @Test
    fun `parses a decimal amount`() {
        assertParsed("0.5 cup cream", 0.5, "cup", "cream")
    }

    @Test
    fun `accepts a comma decimal separator`() {
        assertParsed("0,5 l milk", 0.5, "l", "milk")
    }

    // --- Ranges ---

    @Test
    fun `takes the lower bound of a hyphenated range`() {
        assertParsed("2-3 cloves garlic, minced", 2.0, "clove", "garlic, minced")
    }

    @Test
    fun `takes the lower bound of an en dash range`() {
        assertParsed("2–3 tbsp honey", 2.0, "tbsp", "honey")
    }

    @Test
    fun `takes the lower bound of a spelled out range`() {
        assertParsed("2 to 3 tablespoons butter", 2.0, "tbsp", "butter")
        assertParsed("1 or 2 cloves garlic", 1.0, "clove", "garlic")
    }

    @Test
    fun `does not split a hyphenated word as a range`() {
        // "all-purpose" must not be read as a numeric range.
        assertParsed("all-purpose flour", null, null, "all-purpose flour")
    }

    // --- Parentheticals ---

    @Test
    fun `skips a parenthetical size qualifier between amount and unit`() {
        assertParsed("1 (14 oz) can diced tomatoes", 1.0, "can", "diced tomatoes")
    }

    @Test
    fun `keeps an unbalanced parenthesis in the name rather than swallowing the line`() {
        val result = parseIngredient("1 (14 oz can diced tomatoes")
        assertEquals(1.0, result.quantity)
        assertTrue(result.name.contains("diced tomatoes"), "got: ${result.name}")
    }

    // --- Name handling ---

    @Test
    fun `strips a leading of after the unit`() {
        assertParsed("2 cups of flour", 2.0, "cup", "flour")
    }

    @Test
    fun `leaves an unrecognised word as part of the name`() {
        // "large" is a descriptor, not a unit — quantity still parses.
        assertParsed("3 large eggs", 3.0, null, "large eggs")
    }

    @Test
    fun `collapses internal whitespace`() {
        assertParsed("2   cups    flour", 2.0, "cup", "flour")
    }

    // --- Fallbacks ---

    @Test
    fun `falls back to the whole line when there is no amount`() {
        assertParsed("Salt to taste", null, null, "Salt to taste")
        assertParsed("olive oil", null, null, "olive oil")
    }

    @Test
    fun `falls back to the amount text when the line is only an amount`() {
        // Never returns a blank name, so a UI always has something to show.
        val result = parseIngredient("2 cups")
        assertEquals(2.0, result.quantity)
        assertEquals("cup", result.unit)
        assertEquals("2 cups", result.name)
    }

    @Test
    fun `handles empty and blank input without throwing`() {
        assertParsed("", null, null, "")
        val blank = parseIngredient("   ")
        assertNull(blank.quantity)
        assertEquals("", blank.name)
    }
}
