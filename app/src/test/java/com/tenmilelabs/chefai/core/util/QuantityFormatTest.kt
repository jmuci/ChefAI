package com.tenmilelabs.chefai.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuantityFormatTest {

    @Test
    fun `decimal - whole numbers lose their decimal point`() {
        assertThat(QuantityFormat.decimal(2.0)).isEqualTo("2")
        assertThat(QuantityFormat.decimal(0.0)).isEqualTo("0")
        assertThat(QuantityFormat.decimal(500.0)).isEqualTo("500")
    }

    @Test
    fun `decimal - rounds to two places and trims trailing zeros`() {
        assertThat(QuantityFormat.decimal(1.5)).isEqualTo("1.5")
        assertThat(QuantityFormat.decimal(1.25)).isEqualTo("1.25")
        assertThat(QuantityFormat.decimal(2.0 / 3)).isEqualTo("0.67")
        assertThat(QuantityFormat.decimal(1.0 / 3)).isEqualTo("0.33")
    }

    @Test
    fun `decimal - never uses scientific notation`() {
        assertThat(QuantityFormat.decimal(0.001)).doesNotContain("E")
        assertThat(QuantityFormat.decimal(0.001)).isEqualTo("0")
        // Java's Double.toString switches to an exponent at 1e7, and trimming trailing zeros off
        // one eats the exponent's own digits — "1.00000000005E10" would become a tenth of itself.
        assertThat(QuantityFormat.decimal(12345678.5)).isEqualTo("12345678.5")
        assertThat(QuantityFormat.decimal(10000000000.5)).isEqualTo("10000000000.5")
        assertThat(QuantityFormat.decimal(1.0e20)).isEqualTo("100000000000000000000")
    }

    @Test
    fun `decimal - non-finite values render as themselves rather than throwing`() {
        assertThat(QuantityFormat.decimal(Double.NaN)).isEqualTo("NaN")
        assertThat(QuantityFormat.decimal(Double.POSITIVE_INFINITY)).isEqualTo("Infinity")
    }

    @Test
    fun `cooking - renders common fractions as glyphs`() {
        assertThat(QuantityFormat.cooking(0.5)).isEqualTo("½")
        assertThat(QuantityFormat.cooking(1.0 / 3)).isEqualTo("⅓")
        assertThat(QuantityFormat.cooking(2.0 / 3)).isEqualTo("⅔")
        assertThat(QuantityFormat.cooking(0.25)).isEqualTo("¼")
        assertThat(QuantityFormat.cooking(0.75)).isEqualTo("¾")
        assertThat(QuantityFormat.cooking(0.125)).isEqualTo("⅛")
    }

    @Test
    fun `cooking - keeps the whole part alongside the fraction`() {
        assertThat(QuantityFormat.cooking(1.5)).isEqualTo("1½")
        assertThat(QuantityFormat.cooking(2.25)).isEqualTo("2¼")
        assertThat(QuantityFormat.cooking(4.0 / 3)).isEqualTo("1⅓")
    }

    @Test
    fun `cooking - whole numbers print without a fraction`() {
        assertThat(QuantityFormat.cooking(0.0)).isEqualTo("0")
        assertThat(QuantityFormat.cooking(2.0)).isEqualTo("2")
        assertThat(QuantityFormat.cooking(500.0)).isEqualTo("500")
    }

    @Test
    fun `cooking - a value that is not near a cooking fraction stays decimal`() {
        assertThat(QuantityFormat.cooking(0.6)).isEqualTo("0.6")
        assertThat(QuantityFormat.cooking(0.9)).isEqualTo("0.9")
        assertThat(QuantityFormat.cooking(2.4)).isEqualTo("2.4")
    }

    @Test
    fun `cooking - large amounts stay decimal, where fractions read worse than digits`() {
        // 500g split three ways: "166.67 g" beats "166⅔ g".
        assertThat(QuantityFormat.cooking(500.0 / 3)).isEqualTo("166.67")
        assertThat(QuantityFormat.cooking(250.5)).isEqualTo("250.5")
        assertThat(QuantityFormat.cooking(750.0)).isEqualTo("750")
    }

    @Test
    fun `cooking - picks the nearest fraction, not the first one in range`() {
        // 0.3542 is within tolerance of both ⅓ and ⅜; ⅜ is nearer by a hair.
        assertThat(QuantityFormat.cooking(0.3542)).isEqualTo("⅜")
        assertThat(QuantityFormat.cooking(0.3540)).isEqualTo("⅓")
    }

    @Test
    fun `cooking - a tiny quantity is not collapsed to zero`() {
        assertThat(QuantityFormat.cooking(0.01)).isEqualTo("0.01")
    }

    @Test
    fun `cooking - falls back to decimal for values it cannot render`() {
        assertThat(QuantityFormat.cooking(-1.5)).isEqualTo("-1.5")
        assertThat(QuantityFormat.cooking(Double.NaN)).isEqualTo(QuantityFormat.decimal(Double.NaN))
    }

    @Test
    fun `cooking - metric mass and volume are written in decimals`() {
        // Nobody writes "12½ g". Mixing the two styles down one ingredient column reads as broken.
        assertThat(QuantityFormat.cooking(12.5, "gr")).isEqualTo("12.5")
        assertThat(QuantityFormat.cooking(0.5, "ml")).isEqualTo("0.5")
        assertThat(QuantityFormat.cooking(1.25, "kg")).isEqualTo("1.25")
        assertThat(QuantityFormat.cooking(2.5, "litres")).isEqualTo("2.5")
    }

    @Test
    fun `cooking - kitchen units and countable things keep their fractions`() {
        assertThat(QuantityFormat.cooking(0.5, "cup")).isEqualTo("½")
        assertThat(QuantityFormat.cooking(1.5, "lb")).isEqualTo("1½")
        assertThat(QuantityFormat.cooking(0.25, "tsp")).isEqualTo("¼")
        assertThat(QuantityFormat.cooking(2.0 / 3, "oz")).isEqualTo("⅔")
        assertThat(QuantityFormat.cooking(0.5, "units")).isEqualTo("½")
        assertThat(QuantityFormat.cooking(1.5, "")).isEqualTo("1½")
    }

    @Test
    fun `cooking - the unit is matched however the recipe happens to spell it`() {
        assertThat(QuantityFormat.cooking(0.5, " G ")).isEqualTo("0.5")
        assertThat(QuantityFormat.cooking(0.5, "Gr.")).isEqualTo("0.5")
        assertThat(QuantityFormat.cooking(0.5, "ML")).isEqualTo("0.5")
    }

    @Test
    fun `cooking - an unrecognised unit still gets fractions`() {
        assertThat(QuantityFormat.cooking(0.5, "pinch")).isEqualTo("½")
        assertThat(QuantityFormat.cooking(1.5, "handful")).isEqualTo("1½")
    }
}
