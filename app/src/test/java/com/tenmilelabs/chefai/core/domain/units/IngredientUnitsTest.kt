package com.tenmilelabs.chefai.core.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IngredientUnitsTest {

    private fun read(unit: String, quantity: Double = 1.0) = IngredientUnits.read(quantity, unit)

    @Test
    fun `no amount reads as unmeasured whatever the unit says`() {
        assertThat(read("unit", IngredientUnits.NO_AMOUNT)).isEqualTo(UnitReading.Unmeasured(""))
        assertThat(read("", 0.0)).isEqualTo(UnitReading.Unmeasured(""))
        assertThat(read("cup", Double.NaN)).isEqualTo(UnitReading.Unmeasured(""))
    }

    @Test
    fun `wording that stands in for an amount is unmeasured`() {
        listOf(
            "To taste", "to serve", "Garnish", "For frying", "Sprinkling", "spinkling", "Topping",
            "As required", "part", "parts", "grated, to taste", "drizzle (for cooking)",
            "red deseeded and finely sliced, to serve", "al gusto", "para freír",
        ).forEach { unit ->
            assertThat(read(unit)).isEqualTo(UnitReading.Unmeasured(unit))
        }
    }

    @Test
    fun `a bare descriptor is a count of the ingredient itself, not a unit`() {
        listOf(
            "sliced", "finely chopped", "Chopped", "beaten", "sliced thinly", "Peeled and Sliced",
            "large", "Medium", "whole", "Large Chopped", "small finely diced", "picada",
        ).forEach { unit ->
            assertThat(read(unit)).isEqualTo(UnitReading.Measured(unit = "", note = unit))
        }
    }

    @Test
    fun `a real unit followed by a descriptor keeps the unit and lifts the descriptor off`() {
        assertThat(read("tbs chopped")).isEqualTo(UnitReading.Measured(unit = "tbs", note = "chopped"))
        assertThat(read("tsp ground")).isEqualTo(UnitReading.Measured(unit = "tsp", note = "ground"))
        assertThat(read("cup grated")).isEqualTo(UnitReading.Measured(unit = "cup", note = "grated"))
        assertThat(read("Sprigs of fresh")).isEqualTo(UnitReading.Measured(unit = "Sprigs", note = "of fresh"))
        // A real unit wins over a phrase that follows it: the amount is still in teaspoons.
        assertThat(read("tsp, to taste")).isEqualTo(UnitReading.Measured(unit = "tsp", note = "to taste"))
    }

    @Test
    fun `real units are left exactly as written`() {
        listOf("g", "tablespoons", "tbls", "fl oz", "clove", "cloves", "can", "bunch", "Pinch", "clove peeled crushed")
            .forEach { unit -> assertThat(read(unit)).isEqualTo(UnitReading.Measured(unit = unit)) }
    }

    @Test
    fun `the placeholder and a blank unit are a bare count`() {
        assertThat(read("unit", 2.0)).isEqualTo(UnitReading.Measured(unit = ""))
        assertThat(read("  ", 2.0)).isEqualTo(UnitReading.Measured(unit = ""))
    }

    @Test
    fun `anything unrecognised is kept rather than guessed at`() {
        assertThat(read("Juice of 1")).isEqualTo(UnitReading.Measured(unit = "Juice of 1"))
        assertThat(read("Knob")).isEqualTo(UnitReading.Measured(unit = "Knob"))
    }
}
