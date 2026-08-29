package com.tenmilelabs.chefai.core.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IngredientDensityTest {

    /** The table is written in grams per cup, which is how every published chart states it. */
    private fun gramsPerCup(name: String): Double? =
        IngredientDensity.gramsPerMillilitre(name)?.times(MeasurementUnit.CUP.inBaseUnits)

    @Test
    fun `known staples resolve to their published weight per cup`() {
        assertThat(gramsPerCup("all-purpose flour")).isWithin(TOLERANCE).of(120.0)
        assertThat(gramsPerCup("granulated sugar")).isWithin(TOLERANCE).of(198.0)
        assertThat(gramsPerCup("butter")).isWithin(TOLERANCE).of(227.0)
        assertThat(gramsPerCup("water")).isWithin(TOLERANCE).of(237.0)
    }

    @Test
    fun `cooking oil is not priced like sugar`() {
        // Oils sit near 0.91 g/ml; anything close to sugar's 198 g/cup is a transcription slip.
        val oil = IngredientDensity.gramsPerMillilitre("olive oil")

        assertThat(oil).isNotNull()
        assertThat(oil!!).isWithin(0.03).of(0.91)
    }

    @Test
    fun `a name resolves through the qualifiers recipes actually carry`() {
        assertThat(gramsPerCup("All-Purpose Flour, sifted")).isWithin(TOLERANCE).of(120.0)
        assertThat(gramsPerCup("unsalted butter, softened")).isWithin(TOLERANCE).of(227.0)
    }

    @Test
    fun `the longest matching phrase wins over a shorter one it contains`() {
        // "almond flour" and "brown sugar" both contain a shorter key that would otherwise match.
        assertThat(gramsPerCup("almond flour")).isWithin(TOLERANCE).of(96.0)
        assertThat(gramsPerCup("flour")).isWithin(TOLERANCE).of(120.0)
        assertThat(gramsPerCup("dark brown sugar")).isWithin(TOLERANCE).of(213.0)
        assertThat(gramsPerCup("sugar")).isWithin(TOLERANCE).of(198.0)
    }

    @Test
    fun `matching respects word boundaries`() {
        // "oats" must not be found inside "goats cheese".
        assertThat(IngredientDensity.gramsPerMillilitre("goats cheese")).isNull()
    }

    @Test
    fun `a prepared ingredient has no honest density`() {
        assertThat(IngredientDensity.gramsPerMillilitre("chopped walnuts")).isNull()
        assertThat(IngredientDensity.gramsPerMillilitre("grated parmesan")).isNull()
        assertThat(IngredientDensity.gramsPerMillilitre("shredded chicken")).isNull()
    }

    @Test
    fun `an unknown ingredient returns null rather than a guess`() {
        assertThat(IngredientDensity.gramsPerMillilitre("beef stock")).isNull()
        assertThat(IngredientDensity.gramsPerMillilitre("")).isNull()
        assertThat(IngredientDensity.gramsPerMillilitre("harissa paste")).isNull()
    }

    private companion object {
        const val TOLERANCE = 0.001
    }
}

class UnitNormalizerTest {

    @Test
    fun `recognises the spellings recipe sites publish`() {
        assertThat(UnitNormalizer.normalize("cups")).isEqualTo(MeasurementUnit.CUP)
        assertThat(UnitNormalizer.normalize("Grams")).isEqualTo(MeasurementUnit.GRAM)
        assertThat(UnitNormalizer.normalize("tablespoons")).isEqualTo(MeasurementUnit.TABLESPOON)
        assertThat(UnitNormalizer.normalize("ml.")).isEqualTo(MeasurementUnit.MILLILITRE)
        assertThat(UnitNormalizer.normalize("fl. oz.")).isEqualTo(MeasurementUnit.FLUID_OUNCE)
    }

    @Test
    fun `distinguishes tablespoon from teaspoon by case, as recipe shorthand does`() {
        assertThat(UnitNormalizer.normalize("T")).isEqualTo(MeasurementUnit.TABLESPOON)
        assertThat(UnitNormalizer.normalize("t")).isEqualTo(MeasurementUnit.TEASPOON)
    }

    @Test
    fun `a bare ounce is a weight, not a fluid ounce`() {
        assertThat(UnitNormalizer.normalize("oz")).isEqualTo(MeasurementUnit.OUNCE)
        assertThat(UnitNormalizer.normalize("oz")?.dimension).isEqualTo(UnitDimension.MASS)
    }

    @Test
    fun `counting units and unknown words are not units it converts`() {
        val notConvertible = listOf("clove", "can", "pinch", "slice", "bunch", "unit", "", "punnet")

        for (unit in notConvertible) {
            assertThat(UnitNormalizer.normalize(unit)).isNull()
        }
    }
}
