package com.tenmilelabs.chefai.mealplans.domain.summary

import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.SavedMealPlan
import java.util.UUID

/**
 * One saved plan as the "Saved" tab shows it.
 *
 * @property heroImageModel a Coil model for the card photo: the user's own photo as `file://…` when
 *   they picked one, else the photo of the first recipe that has one, else `null` (placeholder).
 * @property recipeTitles up to three titles of recipes already on this device, in lineup order.
 * @property recipeCount distinct recipes in the lineup, including any not yet on this device.
 */
data class SavedMealPlanSummary(
    val id: UUID,
    val title: String,
    val heroImageModel: String?,
    val recipeTitles: List<String>,
    val recipeCount: Int,
    val dayCount: Int,
    val mealType: MealType,
)

/** Pure projection of saved plans onto [SavedMealPlanSummary]; the ViewModel only feeds it flows. */
object SavedMealPlanSummaryBuilder {

    private const val MAX_LISTED_TITLES = 3

    /** Keeps the order given (the repository already sorts newest first). */
    fun build(
        plans: List<SavedMealPlan>,
        recipes: Map<UUID, RecipePreview>,
    ): List<SavedMealPlanSummary> = plans.map { it.toSummary(recipes) }

    private fun SavedMealPlan.toSummary(recipes: Map<UUID, RecipePreview>): SavedMealPlanSummary {
        val planned = days
            .sortedBy { it.dayIndex }
            .flatMap { listOfNotNull(it.lunchRecipeId, it.dinnerRecipeId) }
            .distinct()
        val previews = planned.mapNotNull { recipes[it] }
        val ownPhoto = heroImagePath?.takeIf { it.isNotBlank() }?.let { "file://$it" }
        val recipePhoto = previews
            .firstOrNull { it.localImagePath != null || it.imageUrlThumbnail.isNotEmpty() }
            ?.let { it.localImagePath?.let { path -> "file://$path" } ?: it.imageUrlThumbnail }
        return SavedMealPlanSummary(
            id = uuid,
            title = title,
            heroImageModel = ownPhoto ?: recipePhoto,
            recipeTitles = previews.take(MAX_LISTED_TITLES).map { it.title },
            recipeCount = planned.size,
            dayCount = days.size,
            mealType = preferences.mealType,
        )
    }
}
