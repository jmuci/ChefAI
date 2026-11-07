package com.tenmilelabs.chefai.mealplans.domain.repository

import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Reusable meal-plan templates ("Saved" plans). Every write is queued for sync. */
interface SavedMealPlanRepository {

    /** The user's saved plans, newest first; deleted ones are excluded. */
    fun observeSavedPlans(userId: UUID): Flow<List<SavedMealPlan>>

    /** The saved plan with [uuid], or `null` if it doesn't exist or was deleted. */
    suspend fun getSavedPlan(uuid: UUID): SavedMealPlan?

    /**
     * Snapshots the meal plan [mealPlanId] as a saved plan keyed [savedPlanId]: its lineup without
     * cooked marks, and its preferences with `planLengthDays` set to the number of days.
     *
     * Fails (without writing) if the plan is missing or has no days, or if [title] — trimmed — is
     * blank or longer than [SavedMealPlan.MAX_TITLE_LENGTH]. [heroImagePath] is the local file the
     * photo was already copied to, if any.
     */
    suspend fun saveFromMealPlan(
        savedPlanId: UUID,
        userId: UUID,
        mealPlanId: UUID,
        title: String,
        heroImagePath: String?,
    ): Result<SavedMealPlan>

    /** Soft-deletes the saved plan and removes its photo file. */
    suspend fun delete(uuid: UUID)
}
