package com.tenmilelabs.chefai.core.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IngredientAmountFormatterTest {

    @Test
    fun `as written - renders the recipe's own amount with cooking fractions`() {
        val label = IngredientAmountFormatter.format(
            quantity = 0.5,
            unit = "cup",
            ingredientName = "all-purpose flour",
            system = MeasurementSystem.AS_WRITTEN,
        )

        assertThat(label.text).isEqualTo("½ cup")
        assertThat(label.isApproximate).isFalse()
    }

    @Test
    fun `metric - a staple crosses to grams and is flagged approximate`() {
        val label = IngredientAmountFormatter.format(
            quantity = 2.0,
            unit = "cups",
            ingredientName = "all-purpose flour",
            system = MeasurementSystem.METRIC,
        )

        assertThat(label.text).isEqualTo("240 g")
        assertThat(label.isApproximate).isTrue()
    }

    @Test
    fun `metric - everything else stays a volume and is exact`() {
        val label = IngredientAmountFormatter.format(
            quantity = 2.0,
            unit = "cups",
            ingredientName = "chicken stock",
            system = MeasurementSystem.METRIC,
        )

        assertThat(label.text).isEqualTo("470 ml")
        assertThat(label.isApproximate).isFalse()
    }

    @Test
    fun `metric amounts render as decimals and imperial ones as fractions`() {
        // QuantityFormat draws this line by unit; the conversion has to land on units it knows.
        val metric = IngredientAmountFormatter.format(0.5, "cup", "stock", MeasurementSystem.METRIC)
        val imperial = IngredientAmountFormatter.format(120.0, "ml", "stock", MeasurementSystem.IMPERIAL)

        assertThat(metric.text).isEqualTo("120 ml")
        assertThat(imperial.text).isEqualTo("½ cup")
    }

    @Test
    fun `a unitless amount reads as a bare number, with no trailing space`() {
        val label = IngredientAmountFormatter.format(2.0, "", "eggs", MeasurementSystem.METRIC)

        assertThat(label.text).isEqualTo("2")
    }

    @Test
    fun `a shopping-list total is rendered as a decimal rather than a cooking fraction`() {
        val label = IngredientAmountFormatter.format(
            quantity = 2.5,
            unit = "cups",
            ingredientName = "rice",
            system = MeasurementSystem.AS_WRITTEN,
            cookingFractions = false,
        )

        assertThat(label.text).isEqualTo("2.5 cups")
    }
}
