package com.tenmilelabs.chefai.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MathUtilsTest {

    @Test
    fun `returns the input unchanged when there is no decimal point`() {
        assertThat(MathUtils.removeTrailingZeros("10")).isEqualTo("10")
    }

    @Test
    fun `strips trailing zeros after the decimal point`() {
        assertThat(MathUtils.removeTrailingZeros("1.500")).isEqualTo("1.5")
    }

    @Test
    fun `strips the decimal point itself when every fractional digit is zero`() {
        assertThat(MathUtils.removeTrailingZeros("100.00")).isEqualTo("100")
    }

    @Test
    fun `leaves a single trailing non-zero fractional digit alone`() {
        assertThat(MathUtils.removeTrailingZeros("1.10")).isEqualTo("1.1")
    }

    @Test
    fun `collapses an all-zero value to its integer part`() {
        assertThat(MathUtils.removeTrailingZeros("0.000")).isEqualTo("0")
    }

    @Test
    fun `leaves a value with no trailing zeros unchanged`() {
        assertThat(MathUtils.removeTrailingZeros("3.14")).isEqualTo("3.14")
    }

    @Test
    fun `handles an empty string without throwing`() {
        assertThat(MathUtils.removeTrailingZeros("")).isEqualTo("")
    }
}
