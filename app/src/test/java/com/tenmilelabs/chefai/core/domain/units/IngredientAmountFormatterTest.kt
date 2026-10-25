package com.tenmilelabs.chefai.core.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IngredientAmountFormatterTest {

    @Test
    fun `a counting unit is pluralised past one and keeps a trailing note as written`() {
        fun text(quantity: Double, unit: String) = IngredientAmountFormatter.format(
            quantity, unit, "garlic", MeasurementSystem.AS_WRITTEN,
        ).text

        assertThat(text(1.0, "clove")).isEqualTo("1 clove")
        assertThat(text(2.0, "clove")).isEqualTo("2 cloves")
        assertThat(text(2.0, "diente")).isEqualTo("2 dientes")
        assertThat(text(2.0, "clove peeled crushed")).isEqualTo("2 clove peeled crushed")
    }

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

    @Test
    fun `the placeholder unit reads as a bare count`() {
        val label = IngredientAmountFormatter.format(
            quantity = 2.0,
            unit = "unit",
            ingredientName = "chicken breasts",
            system = MeasurementSystem.AS_WRITTEN,
        )

        assertThat(label.text).isEqualTo("2")
    }

    @Test
    fun `a Spanish counting unit as the importer now stores it reads in Spanish`() {
        // Given "6 huevos M" (placeholder unit) and "2 dientes de ajo" (stored as "diente")
        val eggs = IngredientAmountFormatter.format(6.0, "unit", "huevos M", MeasurementSystem.AS_WRITTEN)
        val garlic = IngredientAmountFormatter.format(2.0, "diente", "ajo", MeasurementSystem.METRIC)
        val oneClove = IngredientAmountFormatter.format(1.0, "diente", "ajo", MeasurementSystem.AS_WRITTEN)

        // Then no literal "unit", and no English "clove"
        assertThat(eggs.text).isEqualTo("6")
        assertThat(garlic.text).isEqualTo("2 dientes")
        assertThat(oneClove.text).isEqualTo("1 diente")
    }

    @Test
    fun `an ingredient with no amount shows no number and no unit`() {
        // Given a line imported with no amount, scaled for a different serving count
        val label = IngredientAmountFormatter.format(
            quantity = IngredientUnits.NO_AMOUNT * 0.5,
            unit = "unit",
            ingredientName = "Sal y pimienta negra recién molida (al gusto)",
            system = MeasurementSystem.METRIC,
        )

        // Then
        assertThat(label.text).isEmpty()
        assertThat(label.isApproximate).isFalse()
    }

    @Test
    fun `a to-taste phrase in the unit column reads alone, without its placeholder quantity`() {
        // Catalog rows carry "To taste" as the unit behind an invented 1, scaled here to a third.
        val label = IngredientAmountFormatter.format(1.0 / 3, "To taste", "salt", MeasurementSystem.AS_WRITTEN)

        assertThat(label.text).isEqualTo("To taste")
    }
}
