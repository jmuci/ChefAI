package com.tenmilelabs.chefai.mealplans.domain.repository

import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface MealPlanRepository {

    fun observeMealPlansForUser(userId: UUID): Flow<List<MealPlan>>

    fun observeMealPlan(uuid: UUID): Flow<MealPlan?>

    /**
     * A single day by its own id, independent of the plan it belongs to — used by screens (e.g.
     * a recipe opened from a meal plan) that only know the day/slot they were opened from.
     */
    fun observeMealPlanDay(dayId: UUID): Flow<MealPlanDay?>

    suspend fun createMealPlan(mealPlan: MealPlan)

    suspend fun deleteMealPlan(uuid: UUID)

    suspend fun requestGeneration(planId: UUID): Result<Unit>

    /**
     * Fills [planId] via the anonymous-capable stateless generation endpoint, persisting any
     * recipes the device does not already have. Used when the session is anonymous — an
     * authenticated session uses [requestGeneration] instead, which the server can associate with
     * a stored plan.
     *
     * @return the number of days written, or [Result.failure] on a network/server error.
     */
    suspend fun generateStatelessAndSave(
        planId: UUID,
        preferences: MealPlanPreferences,
    ): Result<Int>

    /**
     * Marks a single planned meal cooked, or clears the mark when [cooked] is `false`.
     *
     * Local-only: the sync payload carries no cooked state, so this does not dirty the plan or
     * request a sync. See [com.tenmilelabs.chefai.core.data.local.room.MealPlanDayEntity].
     */
    suspend fun setMealCooked(dayId: UUID, slot: MealSlot, cooked: Boolean)

    /**
     * Replaces a plan's days with a locally generated schedule and moves it to
     * [com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus.READY].
     *
     * Used by the on-device fallback when the backend generator is unreachable or the session is
     * anonymous. The plan is left dirty so a later sync pushes it, after which a server-generated
     * schedule for the same plan overwrites these days.
     */
    suspend fun saveLocallyGeneratedDays(planId: UUID, days: List<MealPlanDay>)
}
