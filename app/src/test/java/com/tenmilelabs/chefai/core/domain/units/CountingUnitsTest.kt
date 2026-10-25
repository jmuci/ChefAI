package com.tenmilelabs.chefai.core.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CountingUnitsTest {

    @Test
    fun `singular and plural spellings resolve to the same unit`() {
        assertThat(CountingUnits.resolve("clove")).isEqualTo(CountingUnits.resolve("Cloves"))
        assertThat(CountingUnits.resolve("diente")).isEqualTo(CountingUnits.resolve("dientes"))
    }

    @Test
    fun `spanish units resolve to spanish wording`() {
        val unit = requireNotNull(CountingUnits.resolve("dientes")) { "dientes is a counting unit" }

        assertThat(CountingUnits.label(unit, 1.0)).isEqualTo("diente")
        assertThat(CountingUnits.label(unit, 3.0)).isEqualTo("dientes")
        assertThat(CountingUnits.resolve("puñado")).isEqualTo(CountingUnits.resolve("punados"))
    }

    @Test
    fun `a trailing preparation note is ignored by resolve but not by resolveExact`() {
        assertThat(CountingUnits.resolve("clove peeled crushed")?.singular).isEqualTo("clove")
        assertThat(CountingUnits.resolve("diente, pelado y machacado")?.singular).isEqualTo("diente")
        assertThat(CountingUnits.resolveExact("clove peeled crushed")).isNull()
    }

    @Test
    fun `trailing words that are not a preparation note do not resolve`() {
        assertThat(CountingUnits.resolve("can of beans")).isNull()
        assertThat(CountingUnits.resolve("clove garlic")).isNull()
    }

    @Test
    fun `measured units and the unknown are not counting units`() {
        assertThat(CountingUnits.resolve("g")).isNull()
        assertThat(CountingUnits.resolve("cup")).isNull()
        assertThat(CountingUnits.resolve("punnet")).isNull()
        assertThat(CountingUnits.resolve("")).isNull()
    }

    @Test
    fun `label is singular up to and including one and plural above it`() {
        val clove = requireNotNull(CountingUnits.resolve("clove")) { "clove is a counting unit" }

        assertThat(CountingUnits.label(clove, 0.5)).isEqualTo("clove")
        assertThat(CountingUnits.label(clove, 1.0)).isEqualTo("clove")
        assertThat(CountingUnits.label(clove, 1.5)).isEqualTo("cloves")
        assertThat(CountingUnits.label(clove, 4.0)).isEqualTo("cloves")
    }

    @Test
    fun `irregular english plurals are spelled out`() {
        assertThat(CountingUnits.resolve("leaves")?.singular).isEqualTo("leaf")
        assertThat(CountingUnits.resolve("pinch")?.plural).isEqualTo("pinches")
    }
}
