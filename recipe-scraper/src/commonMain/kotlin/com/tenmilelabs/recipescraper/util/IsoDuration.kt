package com.tenmilelabs.recipescraper.util

import kotlin.math.floor

/**
 * Parses a schema.org duration into whole minutes.
 *
 * Durations arrive as ISO-8601 (`"PT1H30M"`), which is what the spec asks for, but a fair number of
 * sites emit a bare minute count instead (`"45"`), so both are accepted. Hand-rolled rather than
 * using `java.time.Duration` because this module is `commonMain`.
 *
 * Fractional components are honoured (`"PT1.5H"` is 90 minutes) and the result is rounded to the
 * nearest minute. Date-scale components (years, months, weeks) are ignored rather than treated as
 * failures — a recipe measured in weeks is a data error on the page, not something worth rejecting
 * the whole parse for. Days are honoured, since slow-fermented doughs legitimately use them.
 *
 * Returns `null` for null, blank, or unparseable input, and for a duration of zero.
 */
internal fun parseDurationToMinutes(raw: String?): Int? {
    val text = raw?.trim()?.uppercase() ?: return null
    if (text.isEmpty()) return null

    // Some sites skip ISO-8601 entirely and publish a plain minute count.
    text.toIntOrNull()?.let { return it.takeIf { minutes -> minutes > 0 } }

    if (!text.startsWith("P")) return null

    var totalMinutes = 0.0
    var sawComponent = false
    // The T separator splits date-scale components from time-scale ones. It matters: "PT5M" is five
    // minutes, "P5M" is five months.
    var inTimePart = false
    val number = StringBuilder()

    for (char in text.drop(1)) {
        when {
            char.isDigit() -> number.append(char)

            // ISO-8601 permits either separator for decimal fractions.
            char == '.' || char == ',' -> number.append('.')

            char == 'T' -> {
                inTimePart = true
                number.clear()
            }

            else -> {
                val value = number.toString().toDoubleOrNull()
                number.clear()
                if (value == null) continue
                sawComponent = true
                totalMinutes += when (char) {
                    'D' -> value * MINUTES_PER_DAY
                    'H' -> if (inTimePart) value * MINUTES_PER_HOUR else 0.0
                    'M' -> if (inTimePart) value else 0.0 // outside the time part this is months
                    'S' -> if (inTimePart) value / SECONDS_PER_MINUTE else 0.0
                    else -> 0.0 // 'W'/'Y' and unknown designators: counted as seen, not accumulated
                }
            }
        }
    }

    if (!sawComponent) return null

    // Half-up, so "PT30S" reads as 1 minute. kotlin.math.round breaks ties towards even, which
    // would round that to 0.
    val minutes = floor(totalMinutes + 0.5).toLong()
    return minutes.takeIf { it > 0 }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
}

private const val MINUTES_PER_DAY = 24 * 60.0
private const val MINUTES_PER_HOUR = 60.0
private const val SECONDS_PER_MINUTE = 60.0
