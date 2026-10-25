package com.tenmilelabs.chefai.mealplans.domain.shoppinglist

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShoppingAmountFormatTest {

    private fun format(amount: Double, unit: String) = ShoppingAmountFormat.format(amount, unit)

    @Test
    fun `metric weights and volumes snap to a step a shop sells`() {
        assertThat(format(400.0 / 3, "g")).isEqualTo("135 g")
        assertThat(format(500.0 / 3, "g")).isEqualTo("165 g")
        assertThat(format(2.5, "g")).isEqualTo("2.5 g")
        assertThat(format(1000.0 / 3, "ml")).isEqualTo("330 ml")
        assertThat(format(1.5, "kg")).isEqualTo("1.5 kg")
    }

    @Test
    fun `cups and spoons render as measurable fractions`() {
        assertThat(format(0.12, "cup")).isEqualTo("⅛ cup")
        assertThat(format(0.5, "tbsp")).isEqualTo("½ tbsp")
        assertThat(format(4.0 / 3, "cup")).isEqualTo("1⅓ cup")
        assertThat(format(3.0, "cup")).isEqualTo("3 cup")
    }

    @Test
    fun `counted things round up to a whole one and pluralise on the rounded figure`() {
        assertThat(format(1.0 / 3, "clove")).isEqualTo("1 clove")
        assertThat(format(2.17, "clove")).isEqualTo("3 cloves")
        assertThat(format(0.5, "")).isEqualTo("1")
        assertThat(format(1.0 / 3, "Knob")).isEqualTo("1 Knob")
    }

    @Test
    fun `a whole count carrying floating-point noise is not rounded up past itself`() {
        assertThat(format(6 * (2.0 / 6), "")).isEqualTo("2")
        assertThat(format(3.0, "dientes")).isEqualTo("3 dientes")
    }
}
