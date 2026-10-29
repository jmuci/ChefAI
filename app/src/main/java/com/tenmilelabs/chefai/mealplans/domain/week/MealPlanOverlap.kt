package com.tenmilelabs.chefai.mealplans.domain.week

import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which existing plans a new plan's dates would collide with.
 *
 * Overlap is allowed — the week view shows every plan's meals (ADR-015 amendment) — so this only
 * feeds a non-blocking notice. Pure, so the date arithmetic is unit-tested directly.
 */
object MealPlanOverlap {

    /**
     * The calendar dates [plan] occupies: day 0 is its start date (see [dateOf]).
     * Length is the larger of what the wizard asked for and what was actually generated, so an
     * ungenerated draft still claims the days it is about to fill.
     */
    fun dateRange(plan: MealPlan, zone: ZoneId): ClosedRange<LocalDate> {
        val start = plan.dateOf(dayIndex = 0, zone = zone)
        val generatedLength = (plan.days.maxOfOrNull { it.dayIndex } ?: -1) + 1
        val length = maxOf(plan.preferences.planLengthDays, generatedLength, 1)
        return start..start.plusDays(length - 1L)
    }

    /**
     * Live plans in [plans] that share at least one date with a plan of [lengthDays] starting on
     * [start]. [MealPlanStatus.ARCHIVED] plans never count, matching the week view.
     */
    fun overlapping(
        plans: List<MealPlan>,
        start: LocalDate,
        lengthDays: Int,
        zone: ZoneId,
    ): List<MealPlan> {
        val end = start.plusDays(lengthDays.coerceAtLeast(1) - 1L)
        return plans
            .filter { it.status != MealPlanStatus.ARCHIVED }
            .filter { plan ->
                val range = dateRange(plan, zone)
                range.start <= end && start <= range.endInclusive
            }
    }
}
