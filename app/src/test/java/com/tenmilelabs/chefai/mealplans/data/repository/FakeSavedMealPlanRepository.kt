package com.tenmilelabs.chefai.mealplans.data.repository

import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.mealplans.domain.repository.SavedMealPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** In-memory [SavedMealPlanRepository] that records [saveFromMealPlan] calls. */
class FakeSavedMealPlanRepository : SavedMealPlanRepository {

    data class SaveCall(
        val savedPlanId: UUID,
        val userId: UUID,
        val mealPlanId: UUID,
        val title: String,
        val heroImagePath: String?,
    )

    private val plans = MutableStateFlow<List<SavedMealPlan>>(emptyList())

    val saveCalls: MutableList<SaveCall> = mutableListOf()
    val deletedIds: MutableList<UUID> = mutableListOf()

    /** When set, [saveFromMealPlan] fails with it, like a plan that has no days. */
    var saveFailure: Throwable? = null

    override fun observeSavedPlans(userId: UUID): Flow<List<SavedMealPlan>> =
        plans.map { list -> list.filter { it.userId == userId } }

    override suspend fun getSavedPlan(uuid: UUID): SavedMealPlan? = plans.value.firstOrNull { it.uuid == uuid }

    override suspend fun saveFromMealPlan(
        savedPlanId: UUID,
        userId: UUID,
        mealPlanId: UUID,
        title: String,
        heroImagePath: String?,
    ): Result<SavedMealPlan> {
        saveCalls += SaveCall(savedPlanId, userId, mealPlanId, title, heroImagePath)
        saveFailure?.let { return Result.failure(it) }
        val saved = SavedMealPlan(
            uuid = savedPlanId,
            userId = userId,
            title = title,
            heroImagePath = heroImagePath,
            preferences = MealPlanPreferences(
                planLengthDays = 5,
                mealType = MealType.DINNER,
                dietaryRestrictions = emptySet<DietaryRestriction>(),
                recipeSource = RecipeSource.COLLECTION_ONLY,
                maxPrepTimeMinutes = null,
                servingsPerMeal = 2,
                batchCooking = false,
                leftoverFriendly = false,
                varietyPreference = VarietyPreference.MEDIUM,
            ),
            days = emptyList(),
            sourceMealPlanId = mealPlanId,
            createdAt = 0L,
            updatedAt = 0L,
        )
        plans.value += saved
        return Result.success(saved)
    }

    override suspend fun delete(uuid: UUID) {
        deletedIds += uuid
        plans.value = plans.value.filterNot { it.uuid == uuid }
    }
}
