package com.tenmilelabs.chefai.core.util

import kotlin.math.roundToLong

private const val VULGAR_FRACTIONS = "½⅓⅔¼¾⅛"

/**
 * One "amount unit" pair: a whole/decimal number optionally followed by a fraction ("1 1/2",
 * "1½"), a bare fraction ("1/2", "½"), then a time unit.
 */
private val DURATION_PART_REGEX = Regex(
    """(\d+(?:\.\d+)?(?:\s+\d+/\d+|\s*[$VULGAR_FRACTIONS])?|\d+/\d+|[$VULGAR_FRACTIONS])\s*""" +
        """(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\b""",
    RegexOption.IGNORE_CASE,
)

/** What may sit between the parts of one compound duration: "1 hour 15 minutes", "1 hour and 15 minutes". */
private val COMPOUND_SEPARATOR_REGEX = Regex("""\s*(?:,|and)?\s*""", RegexOption.IGNORE_CASE)

/**
 * Extracts a countdown duration from free-text recipe step instructions, e.g.
 * "Bake for 30 minutes" -> 1800, "Let rest for 1 hour 15 minutes" -> 4500, "Simmer 1 1/2 hours"
 * -> 5400.
 *
 * Only the **first** duration expression is used, including the parts directly joined to it
 * ("1 hour 15 minutes"). Durations in separate clauses are not added together: "Marinate for
 * 2 hours, then grill 10 minutes" is a 2-hour timer, not 2h 10m. In a range like "10-15 minutes"
 * the upper bound wins — the regex can't match the unit after "10-", so the first match is "15".
 *
 * Returns null when no numeric duration is found (e.g. "overnight", "until golden brown") — those
 * phrases have no fixed length to count down from.
 */
fun parseStepDurationSeconds(instruction: String): Long? {
    val parts = DURATION_PART_REGEX.findAll(instruction).toList()
    val first = parts.firstOrNull() ?: return null

    var totalSeconds = first.toSeconds() ?: return null
    var previous = first
    for (next in parts.drop(1)) {
        val gap = instruction.substring(previous.range.last + 1, next.range.first)
        if (!COMPOUND_SEPARATOR_REGEX.matches(gap)) break
        totalSeconds += next.toSeconds() ?: break
        previous = next
    }
    return totalSeconds.roundToLong().takeIf { it > 0 }
}

private fun MatchResult.toSeconds(): Double? {
    val amount = parseAmount(groupValues[1].trim()) ?: return null
    val unitSeconds = when (groupValues[2].lowercase().first()) {
        'h' -> 3600
        'm' -> 60
        else -> 1
    }
    return amount * unitSeconds
}

private fun parseAmount(raw: String): Double? {
    raw.toDoubleOrNull()?.let { return it }
    val vulgar = raw.lastOrNull()?.let(::vulgarFractionValue)
    if (vulgar != null) {
        val whole = raw.dropLast(1).trim()
        return (if (whole.isEmpty()) 0.0 else whole.toDoubleOrNull() ?: return null) + vulgar
    }
    val pieces = raw.split(Regex("""\s+"""))
    val fraction = pieces.last().split('/').takeIf { it.size == 2 } ?: return null
    val numerator = fraction[0].toDoubleOrNull() ?: return null
    val denominator = fraction[1].toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
    val whole = if (pieces.size > 1) pieces.first().toDoubleOrNull() ?: return null else 0.0
    return whole + numerator / denominator
}

private fun vulgarFractionValue(c: Char): Double? = when (c) {
    '½' -> 1.0 / 2
    '⅓' -> 1.0 / 3
    '⅔' -> 2.0 / 3
    '¼' -> 1.0 / 4
    '¾' -> 3.0 / 4
    '⅛' -> 1.0 / 8
    else -> null
}
