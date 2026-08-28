package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Fills a meal plan from the recipes already on the device.
 *
 * The universal fallback when the primary generation path — see
 * [GenerateMealPlanUseCase] — doesn't fill the plan: the server round trip is unreachable or
 * errors, the anonymous-capable stateless endpoint fails, or the session is anonymous with
 * `COLLECTION_ONLY` (a query the server can't answer, since "my collection" exists only on this
 * device). Without it a plan created offline stays an empty DRAFT forever.
 *
 * A server-generated schedule for the same plan overwrites these days on the next pull, so this is
 * a stop-gap rather than a competing source of truth.
 */
class LocalMealPlanGenerator @Inject constructor(
    private val recipesRepository: RecipesRepository,
    private val mealPlanRepository: MealPlanRepository,
) {

    /**
     * Schedules [plan] from local recipes and persists the result.
     *
     * @return the number of days written, or [Result.failure] when the device holds no recipe the
     *   plan could draw from.
     */
    suspend operator fun invoke(plan: MealPlan): Result<Int> {
        val candidates = when (plan.preferences.recipeSource) {
            RecipeSource.COLLECTION_ONLY ->
                recipesRepository.getRecipesPreviewStreamForUser(plan.userId).first()
            RecipeSource.INCLUDE_PUBLIC ->
                recipesRepository.getRecipesPreviewStream().first()
        }

        val days = MealPlanScheduler.schedule(
            preferences = plan.preferences,
            candidates = candidates,
            newDayId = UuidV7Generator::newId,
        )

        if (days.isEmpty()) {
            Timber.w("LocalMealPlanGenerator: no candidate recipes for plan ${plan.uuid}")
            return Result.failure(NoRecipesAvailableException())
        }

        mealPlanRepository.saveLocallyGeneratedDays(plan.uuid, days)
        return Result.success(days.size)
    }

    /** Raised when the device holds no recipe the plan could be built from. */
    class NoRecipesAvailableException : Exception("No recipes available to build a meal plan from")
}
