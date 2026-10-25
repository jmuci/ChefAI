package com.tenmilelabs.recipescraper.language

import com.tenmilelabs.recipescraper.model.DetectedLanguage
import com.tenmilelabs.recipescraper.model.LanguageConfidence.HIGH
import com.tenmilelabs.recipescraper.model.LanguageConfidence.LOW
import com.tenmilelabs.recipescraper.model.LanguageSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguageDetectorTest {

    // --- normalizeLanguageTag ---

    @Test
    fun `collapses regional variants to the primary subtag`() {
        assertEquals("es", normalizeLanguageTag("es-MX"))
        assertEquals("es", normalizeLanguageTag("es_ES"))
        assertEquals("es", normalizeLanguageTag("ES"))
        assertEquals("en", normalizeLanguageTag(" en-US, en "))
    }

    @Test
    fun `rejects anything that is not a two-letter code`() {
        assertNull(normalizeLanguageTag(null))
        assertNull(normalizeLanguageTag(""))
        assertNull(normalizeLanguageTag("spa"))
        assertNull(normalizeLanguageTag("x-default"))
        assertNull(normalizeLanguageTag("Spanish"))
    }

    // --- scoreText ---

    @Test
    fun `scores English recipe text as English`() {
        val verdict = scoreText(
            "Preheat the oven. Mix the flour and sugar with the butter, then add the eggs and milk. " +
                "Bake for 20 minutes until golden.",
        )
        assertEquals(TextVerdict("en", confident = true), verdict)
    }

    @Test
    fun `scores Spanish recipe text as Spanish`() {
        val verdict = scoreText(
            "Precalentar el horno. Mezclar la harina y el azúcar con la mantequilla, añadir los huevos " +
                "y la leche. Hornear durante 20 minutos hasta que esté dorado.",
        )
        assertEquals(TextVerdict("es", confident = true), verdict)
    }

    @Test
    fun `does not mistake Portuguese for Spanish`() {
        val verdict = scoreText(
            "Preaqueça o forno. Misture a farinha e o açúcar com a manteiga, adicione os ovos e o " +
                "leite. Asse durante 20 minutos até dourar. Sirva com uma colher de sopa de mel.",
        )
        assertEquals("pt", verdict?.code)
        assertTrue(verdict?.confident == true)
    }

    @Test
    fun `is not confident about a handful of words`() {
        val verdict = scoreText("Gazpacho. 1 kg tomates.")
        assertFalse(verdict?.confident ?: false)
    }

    @Test
    fun `returns null when no profile word appears`() {
        assertNull(scoreText("Gazpacho 1 kg tomates"))
        assertNull(scoreText(""))
    }

    @Test
    fun `returns null on a tie rather than favouring the first profile`() {
        // One English word, one Spanish word: no lean either way.
        assertNull(scoreText("the leche"))
    }

    // --- decideLanguage ---

    private val confidentSpanish = TextVerdict("es", confident = true)
    private val inconclusive = TextVerdict("es", confident = false)
    private val leansEnglish = TextVerdict("en", confident = false)

    @Test
    fun `markup and text agreeing is HIGH, credited to the markup`() {
        assertEquals(
            DetectedLanguage("es", HIGH, LanguageSource.HTML_LANG),
            decideLanguage(listOf(LanguageSignal("es", LanguageSource.HTML_LANG)), confidentSpanish),
        )
    }

    @Test
    fun `confident text with no markup is HIGH`() {
        assertEquals(DetectedLanguage("es", HIGH, LanguageSource.TEXT), decideLanguage(emptyList(), confidentSpanish))
    }

    @Test
    fun `confident text overrides disagreeing markup, but only at LOW confidence`() {
        assertEquals(
            DetectedLanguage("es", LOW, LanguageSource.TEXT),
            decideLanguage(listOf(LanguageSignal("en", LanguageSource.HTML_LANG)), confidentSpanish),
        )
    }

    @Test
    fun `explicit inLanguage with inconclusive text is HIGH`() {
        assertEquals(
            DetectedLanguage("es", HIGH, LanguageSource.JSON_LD),
            decideLanguage(listOf(LanguageSignal("es", LanguageSource.JSON_LD)), inconclusive),
        )
        assertEquals(
            DetectedLanguage("es", HIGH, LanguageSource.MICRODATA),
            decideLanguage(listOf(LanguageSignal("es", LanguageSource.MICRODATA)), null),
        )
    }

    @Test
    fun `page-level markup with a weak text lean the same way is HIGH`() {
        assertEquals(
            DetectedLanguage("es", HIGH, LanguageSource.OG_LOCALE),
            decideLanguage(listOf(LanguageSignal("es", LanguageSource.OG_LOCALE)), inconclusive),
        )
    }

    @Test
    fun `page-level markup with a weak text lean elsewhere is LOW`() {
        assertEquals(
            DetectedLanguage("es", LOW, LanguageSource.HTML_LANG),
            decideLanguage(listOf(LanguageSignal("es", LanguageSource.HTML_LANG)), leansEnglish),
        )
    }

    @Test
    fun `page-level markup with no text lean at all is LOW`() {
        assertEquals(
            DetectedLanguage("es", LOW, LanguageSource.HTTP_HEADER),
            decideLanguage(listOf(LanguageSignal("es", LanguageSource.HTTP_HEADER)), null),
        )
    }

    @Test
    fun `no markup and inconclusive text is null`() {
        assertNull(decideLanguage(emptyList(), inconclusive))
        assertNull(decideLanguage(emptyList(), null))
    }

    @Test
    fun `the highest-priority markup signal wins`() {
        val signals = listOf(
            LanguageSignal("es", LanguageSource.JSON_LD),
            LanguageSignal("en", LanguageSource.HTML_LANG),
        )
        assertEquals(DetectedLanguage("es", HIGH, LanguageSource.JSON_LD), decideLanguage(signals, confidentSpanish))
    }

    @Test
    fun `text cannot contradict markup naming a language it has no profile for`() {
        // Dutch shares "de" with Spanish; without a Dutch profile, a Spanish score proves nothing.
        assertEquals(
            DetectedLanguage("nl", LOW, LanguageSource.HTML_LANG),
            decideLanguage(listOf(LanguageSignal("nl", LanguageSource.HTML_LANG)), confidentSpanish),
        )
    }
}
