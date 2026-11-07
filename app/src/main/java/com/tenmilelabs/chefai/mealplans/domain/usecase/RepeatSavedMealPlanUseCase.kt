package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.mealplans.domain.repository.SavedMealPlanRepository
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import timber.log.Timber
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * Schedules a copy of a saved plan starting on a chosen date.
 *
 * The new dated plan is its own row, not a view of the template: it carries the template's title,
 * preferences and lineup, with fresh day ids and no cooked marks. The generator is never called —
 * the point of repeating is getting the same meals again.
 */
class RepeatSavedMealPlanUseCase @Inject constructor(
    private val savedMealPlanRepository: SavedMealPlanRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val recipesRepository: RecipesRepository,
    private val clock: Clock,
) {

    /**
     * @return the new plan's id, or a failure if the template is missing (nothing is created then).
     */
    suspend operator fun invoke(userId: UUID, savedPlanId: UUID, startDate: LocalDate): Result<UUID> {
        return try {
            val template = savedMealPlanRepository.getSavedPlan(savedPlanId)
                ?: return Result.failure(NoSuchElementException("Saved plan $savedPlanId not found"))

            // Only ids known to be soft-deleted are dropped: an id with no local row may just not
            // have synced yet, and `getOrFetchRecipe` resolves it when the plan is opened.
            val deleted = recipesRepository.getSoftDeletedRecipeIds(
                template.days.flatMap { listOfNotNull(it.lunchRecipeId, it.dinnerRecipeId) },
            )
            val days = template.days.map { day ->
                MealPlanDay(
                    uuid = UuidV7Generator.newId(),
                    dayIndex = day.dayIndex,
                    lunchRecipeId = day.lunchRecipeId?.takeUnless { it in deleted },
                    dinnerRecipeId = day.dinnerRecipeId?.takeUnless { it in deleted },
                )
            }

            val now = clock.millis()
            val planId = UuidV7Generator.newId()
            mealPlanRepository.createMealPlan(
                MealPlan(
                    uuid = planId,
                    userId = userId,
                    name = template.title,
                    preferences = template.preferences,
                    status = MealPlanStatus.DRAFT,
                    createdAt = now,
                    updatedAt = now,
                    days = emptyList(),
                    startDate = startDate,
                ),
            )
            mealPlanRepository.saveLocallyGeneratedDays(planId, days)
            Result.success(planId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "RepeatSavedMealPlanUseCase: failed for saved plan $savedPlanId")
            Result.failure(e)
        }
    }
}
