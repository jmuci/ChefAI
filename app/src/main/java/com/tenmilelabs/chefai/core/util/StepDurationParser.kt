package com.tenmilelabs.chefai.core.util

import kotlin.math.roundToLong

private val HOUR_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:hours?|hrs?|h)\b""", RegexOption.IGNORE_CASE)
private val MINUTE_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE)
private val SECOND_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:seconds?|secs?|s)\b""", RegexOption.IGNORE_CASE)

/**
 * Extracts a countdown duration from free-text recipe step instructions, e.g.
 * "Bake for 30 minutes" -> 1800, "Let rest for 1 hour 15 minutes" -> 4500. When a unit appears
 * more than once (a range like "10-15 minutes"), the last match wins — regex alternation can't
 * match starting mid-number, so the leftmost successful match is the second number.
 *
 * Returns null when no numeric duration is found (e.g. "overnight", "until golden brown") — those
 * phrases have no fixed length to count down from.
 */
fun parseStepDurationSeconds(instruction: String): Long? {
    val hours = HOUR_REGEX.find(instruction)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    val minutes = MINUTE_REGEX.find(instruction)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    val seconds = SECOND_REGEX.find(instruction)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    val totalSeconds = (hours * 3600 + minutes * 60 + seconds).roundToLong()
    return totalSeconds.takeIf { it > 0 }
}
