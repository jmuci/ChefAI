package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.week.dateOf
import com.tenmilelabs.chefai.mealplans.domain.week.planDateFor
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/** Which plan, and which day within it, a calendar date resolves to. */
data class PlanDayTarget(val planId: UUID, val dayIndex: Int)

/**
 * Binds one recipe to one calendar date's [MealSlot].
 *
 * "Put this recipe on Thursday" has two cases:
 * - a live plan of the user's already spans that date (see [dateOf]): the recipe goes into that
 *   plan's day;
 * - none does: a new one-day plan is created *starting on that date*, holding just that meal. Its
 *   `createdAt` is also that date's local noon, so a device that never receives `startDate` (the
 *   backend does not store it yet) still projects it onto the same row via [planDateFor].
 *
 * Only [MealPlanStatus.READY] plans are written into. A draft or generating plan has its days
 * replaced wholesale when generation lands, which would silently drop the meal just added.
 */
class AddRecipeToDateUseCase @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val clock: Clock,
) {

    /**
     * @param plans the user's current plans — passed in rather than read here so the caller's
     *   already-observed snapshot is what gets matched against.
     * @return `false` when the write found no plan row to update; a thrown exception is a storage
     *   failure and is the caller's to report.
     */
    suspend operator fun invoke(
        userId: UUID,
        plans: List<MealPlan>,
        date: LocalDate,
        slot: MealSlot,
        recipeId: UUID,
    ): Boolean {
        val target = findTarget(plans, userId, date, clock.zone)
        if (target != null) {
            return mealPlanRepository.assignRecipeToSlot(target.planId, target.dayIndex, slot, recipeId)
        }

        val now = clock.millis()
        val day = MealPlanDay(
            uuid = UuidV7Generator.newId(),
            dayIndex = 0,
            dinnerRecipeId = recipeId.takeIf { slot == MealSlot.DINNER },
            lunchRecipeId = recipeId.takeIf { slot == MealSlot.LUNCH },
        )
        mealPlanRepository.createMealPlan(
            MealPlan(
                uuid = UuidV7Generator.newId(),
                userId = userId,
                // Not MealPlanSummaryBuilder.DEFAULT_PLAN_NAME: that name asks the UI to invent a
                // "<tag> week" title, which would be wrong for a single meal.
                name = SINGLE_MEAL_PLAN_NAME,
                preferences = SINGLE_DAY_PREFERENCES,
                status = MealPlanStatus.READY,
                createdAt = date.atTime(NOON).atZone(clock.zone).toInstant().toEpochMilli(),
                updatedAt = now,
                days = listOf(day),
                startDate = date,
            )
        )
        return true
    }

    companion object {
        const val SINGLE_MEAL_PLAN_NAME: String = "Added meals"

        private val NOON = LocalTime.NOON

        private val SINGLE_DAY_PREFERENCES = MealPlanPreferences(
            planLengthDays = 1,
            mealType = MealType.DINNER_AND_LUNCH,
            dietaryRestrictions = emptySet(),
            recipeSource = RecipeSource.COLLECTION_ONLY,
            maxPrepTimeMinutes = null,
            servingsPerMeal = 1,
            batchCooking = false,
            leftoverFriendly = false,
            varietyPreference = VarietyPreference.MEDIUM,
        )

        /**
         * The oldest of the user's own [MealPlanStatus.READY] plans whose span covers [date], or
         * `null`. A span is the longer of `planLengthDays` and the last stored day, since
         * `appendRecipeAsNewDay` can leave days past the nominal length.
         */
        fun findTarget(
            plans: List<MealPlan>,
            userId: UUID,
            date: LocalDate,
            zone: ZoneId,
        ): PlanDayTarget? = plans
            .asSequence()
            .filter { it.userId == userId && it.status == MealPlanStatus.READY }
            .sortedWith(compareBy({ it.createdAt }, { it.uuid }))
            .firstNotNullOfOrNull { plan ->
                val offset = ChronoUnit.DAYS.between(plan.dateOf(0, zone), date)
                val span = maxOf(plan.preferences.planLengthDays, (plan.days.maxOfOrNull { it.dayIndex } ?: -1) + 1)
                if (offset in 0 until span) PlanDayTarget(plan.uuid, offset.toInt()) else null
            }
    }
}
