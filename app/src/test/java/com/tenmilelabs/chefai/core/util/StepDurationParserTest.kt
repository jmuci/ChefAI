package com.tenmilelabs.chefai.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StepDurationParserTest {

    @Test
    fun `parses a plain minutes phrase`() {
        assertThat(parseStepDurationSeconds("Bake for 30 minutes")).isEqualTo(30 * 60L)
    }

    @Test
    fun `parses hours and minutes together`() {
        assertThat(parseStepDurationSeconds("Let rest for 1 hour 15 minutes")).isEqualTo((60 + 15) * 60L)
    }

    @Test
    fun `parses an hour alone`() {
        assertThat(parseStepDurationSeconds("Simmer for 1 hour")).isEqualTo(3600L)
    }

    @Test
    fun `parses abbreviated units without a space`() {
        assertThat(parseStepDurationSeconds("Rest 1h 15m")).isEqualTo((60 + 15) * 60L)
    }

    @Test
    fun `parses abbreviated minutes`() {
        assertThat(parseStepDurationSeconds("Cook for 5 mins")).isEqualTo(5 * 60L)
    }

    @Test
    fun `parses seconds`() {
        assertThat(parseStepDurationSeconds("Sear for 45 seconds per side")).isEqualTo(45L)
    }

    @Test
    fun `is case insensitive`() {
        assertThat(parseStepDurationSeconds("BAKE FOR 20 MINUTES")).isEqualTo(20 * 60L)
    }

    @Test
    fun `takes the upper bound of a range`() {
        assertThat(parseStepDurationSeconds("Bake for 10-15 minutes")).isEqualTo(15 * 60L)
    }

    @Test
    fun `returns null when no numeric duration is present`() {
        assertThat(parseStepDurationSeconds("Let rest overnight")).isNull()
        assertThat(parseStepDurationSeconds("Cook until golden brown")).isNull()
        assertThat(parseStepDurationSeconds("Add salt to taste")).isNull()
    }

    @Test
    fun `does not false-positive on unrelated numbers`() {
        assertThat(parseStepDurationSeconds("Preheat oven to 350 degrees")).isNull()
        assertThat(parseStepDurationSeconds("Slice into 8 pieces")).isNull()
    }
}
