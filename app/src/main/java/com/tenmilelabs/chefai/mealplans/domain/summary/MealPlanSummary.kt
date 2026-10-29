package com.tenmilelabs.chefai.mealplans.domain.summary

import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.week.dateOf
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * One plan as the plans list shows it: a hero recipe, a title, a few of its recipes, and when it
 * runs.
 *
 * @property customName the name the user gave the plan, or `null` when it still carries the
 *   generated default — the UI then builds a title from [dominantTag].
 * @property dominantTag the tag most of the plan's recipes share ("Italian"), the raw material for a
 *   generated title; `null` when the recipes carry none or none are on this device yet.
 * @property heroRecipe the recipe whose photo fronts the card: the first uncooked one with an image,
 *   else the first planned one with an image, else any planned one, else `null`.
 * @property lastCookedOn the most recent date any meal was marked cooked, so an old plan can say
 *   when it was last used instead of a schedule it no longer has.
 */
data class MealPlanSummary(
    val planId: UUID,
    val customName: String?,
    val dominantTag: String?,
    val heroRecipe: RecipePreview?,
    val recipeTitles: List<String>,
    val recipeCount: Int,
    val dayCount: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lastCookedOn: LocalDate?,
    val isShared: Boolean,
) {
    /** True once the plan's last day is behind [today] — it is history, not a schedule. */
    fun isPast(today: LocalDate): Boolean = endDate.isBefore(today)
}

/** Pure projection of plans onto [MealPlanSummary]; the ViewModel only feeds it flows. */
object MealPlanSummaryBuilder {

    /** The name the wizard gives a plan the user did not title — matches `meal_plan_default_name`. */
    const val DEFAULT_PLAN_NAME: String = "Week plan"

    private const val MAX_LISTED_TITLES = 3

    /** Newest first. Archived plans are dropped, as they are from the week view. */
    fun build(
        plans: List<MealPlan>,
        recipes: Map<UUID, RecipePreview>,
        zone: ZoneId,
    ): List<MealPlanSummary> = plans
        .filter { it.status != MealPlanStatus.ARCHIVED }
        .sortedWith(compareByDescending<MealPlan> { it.createdAt }.thenBy { it.uuid })
        .map { it.toSummary(recipes, zone) }

    private fun MealPlan.toSummary(recipes: Map<UUID, RecipePreview>, zone: ZoneId): MealPlanSummary {
        val ordered = days.sortedBy { it.dayIndex }
        val planned = ordered
            .flatMap { listOfNotNull(it.lunchRecipeId, it.dinnerRecipeId) }
            .distinct()
        val previews = planned.mapNotNull { recipes[it] }
        // A recipe counts as uncooked while any slot it fills is still outstanding.
        val uncookedPreviews = ordered
            .flatMap { day ->
                listOf(day.lunchRecipeId to day.lunchCookedAt, day.dinnerRecipeId to day.dinnerCookedAt)
            }
            .filter { (recipeId, cookedAt) -> recipeId != null && cookedAt == null }
            .mapNotNull { (recipeId, _) -> recipes[recipeId] }
            .distinctBy { it.uuid }

        val lastCookedMillis = days
            .flatMap { listOfNotNull(it.lunchCookedAt, it.dinnerCookedAt) }
            .maxOrNull()

        val length = preferences.planLengthDays.coerceAtLeast(1)
        return MealPlanSummary(
            planId = uuid,
            customName = name.trim().takeUnless { it.isEmpty() || it == DEFAULT_PLAN_NAME },
            dominantTag = previews
                .flatMap { recipe -> recipe.tags.map { it.displayName }.distinct() }
                .groupingBy { it }
                .eachCount()
                .entries
                // Ties break alphabetically so the title does not flicker between recompositions.
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()
                ?.key,
            heroRecipe = heroFor(uncooked = uncookedPreviews, all = previews),
            recipeTitles = previews.take(MAX_LISTED_TITLES).map { it.title },
            recipeCount = planned.size,
            dayCount = length,
            startDate = dateOf(0, zone),
            endDate = dateOf(length - 1, zone),
            lastCookedOn = lastCookedMillis?.let {
                java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
            },
            isShared = householdId != null,
        )
    }

    /**
     * What is left to cook fronts the card, so a half-finished plan shows its next meal rather
     * than the one already eaten. Prefers a recipe with a photo; falls back to any recipe once the
     * plan is fully cooked.
     */
    private fun heroFor(uncooked: List<RecipePreview>, all: List<RecipePreview>): RecipePreview? =
        uncooked.firstOrNull { it.hasImage() }
            ?: all.firstOrNull { it.hasImage() }
            ?: uncooked.firstOrNull()
            ?: all.firstOrNull()

    private fun RecipePreview.hasImage(): Boolean =
        localImagePath != null || imageUrlThumbnail.isNotEmpty()
}
