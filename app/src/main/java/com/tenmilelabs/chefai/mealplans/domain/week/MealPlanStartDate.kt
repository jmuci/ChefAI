package com.tenmilelabs.chefai.mealplans.domain.week

import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.time.LocalDate
import java.time.ZoneId

/**
 * The start date the wizard pre-selects: the day after the last date that already has a meal, so a
 * new plan picks up where the calendar's planned meals end rather than stacking on top of them.
 *
 * Gaps before that last meal are skipped on purpose — Mon planned, Tue empty, Wed planned gives
 * Thu, not Tue: a plan is a run of consecutive days, and starting it in a one-day hole would
 * overlap Wednesday anyway.
 *
 * @param week when the wizard was opened from the week view, the week on screen: only meals inside
 *   it count, and an empty week defaults to its first day. `null` (any other entry point) considers
 *   every future meal. Past dates never count and are never returned — the result is at least [today].
 * @param zone the zone plan dates are read in; see [dateOf].
 */
fun defaultMealPlanStartDate(
    plans: List<MealPlan>,
    today: LocalDate,
    zone: ZoneId,
    week: ClosedRange<LocalDate>? = null,
): LocalDate {
    val from = maxOf(week?.start ?: today, today)
    val until = week?.endInclusive
    val lastPlanned = plans
        .asSequence()
        .filter { it.status != MealPlanStatus.ARCHIVED }
        .flatMap { plan ->
            plan.days.asSequence()
                .filter { day -> MealSlot.entries.any { day.recipeIdFor(it) != null } }
                .map { plan.dateOf(it.dayIndex, zone) }
        }
        .filter { it >= from && (until == null || it <= until) }
        .maxOrNull()
    return lastPlanned?.plusDays(1) ?: from
}
