package com.tenmilelabs.chefai.core.domain.model

import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/** The day the meal-plan calendar treats as the first day of a week. */
enum class WeekStart(val dayOfWeek: DayOfWeek) {
    MONDAY(DayOfWeek.MONDAY),
    SUNDAY(DayOfWeek.SUNDAY),
    ;

    companion object {
        /** What the device locale says, for a user who has not chosen yet. */
        fun forLocale(locale: Locale = Locale.getDefault()): WeekStart =
            if (WeekFields.of(locale).firstDayOfWeek == DayOfWeek.SUNDAY) SUNDAY else MONDAY

        /** [name] back to an entry, falling back to the locale default for anything unrecognised. */
        fun fromName(name: String?): WeekStart =
            entries.firstOrNull { it.name == name } ?: forLocale()
    }
}
