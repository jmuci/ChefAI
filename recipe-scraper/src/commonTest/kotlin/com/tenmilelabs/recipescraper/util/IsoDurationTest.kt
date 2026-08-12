package com.tenmilelabs.recipescraper.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IsoDurationTest {

    @Test
    fun `parses hours and minutes`() {
        assertEquals(90, parseDurationToMinutes("PT1H30M"))
    }

    @Test
    fun `parses minutes only`() {
        assertEquals(30, parseDurationToMinutes("PT30M"))
    }

    @Test
    fun `parses hours only`() {
        assertEquals(120, parseDurationToMinutes("PT2H"))
    }

    @Test
    fun `parses full form with zero components`() {
        assertEquals(45, parseDurationToMinutes("P0DT0H45M"))
    }

    @Test
    fun `parses trailing zero seconds`() {
        assertEquals(90, parseDurationToMinutes("PT1H30M0S"))
    }

    @Test
    fun `rounds seconds to nearest minute`() {
        assertEquals(2, parseDurationToMinutes("PT90S"))
        assertEquals(1, parseDurationToMinutes("PT40S"))
        assertEquals(1, parseDurationToMinutes("PT30S"))
    }

    @Test
    fun `honours days for long ferments`() {
        assertEquals(1500, parseDurationToMinutes("P1DT1H"))
    }

    @Test
    fun `accepts lowercase and surrounding whitespace`() {
        assertEquals(90, parseDurationToMinutes("  pt1h30m  "))
    }

    @Test
    fun `accepts a bare minute count`() {
        assertEquals(45, parseDurationToMinutes("45"))
    }

    @Test
    fun `honours fractional components`() {
        assertEquals(90, parseDurationToMinutes("PT1.5H"))
        // ISO-8601 allows a comma as the decimal separator too.
        assertEquals(90, parseDurationToMinutes("PT1,5H"))
        assertEquals(30, parseDurationToMinutes("PT0.5H"))
    }

    @Test
    fun `ignores date-scale components without failing`() {
        // P1M is one month, not one minute — must not be read as time.
        assertNull(parseDurationToMinutes("P1M"))
        // Weeks are ignored, so only the time part counts.
        assertEquals(30, parseDurationToMinutes("P1WT30M"))
    }

    @Test
    fun `distinguishes months from minutes by the T separator`() {
        assertEquals(5, parseDurationToMinutes("PT5M"))
        assertNull(parseDurationToMinutes("P5M"))
    }

    @Test
    fun `returns null for zero durations`() {
        assertNull(parseDurationToMinutes("PT0M"))
        assertNull(parseDurationToMinutes("0"))
    }

    @Test
    fun `returns null for null blank and garbage`() {
        assertNull(parseDurationToMinutes(null))
        assertNull(parseDurationToMinutes(""))
        assertNull(parseDurationToMinutes("   "))
        assertNull(parseDurationToMinutes("about an hour"))
        assertNull(parseDurationToMinutes("PT"))
        assertNull(parseDurationToMinutes("P"))
    }
}
