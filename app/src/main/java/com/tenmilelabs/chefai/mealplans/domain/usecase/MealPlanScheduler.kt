package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanDay
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import java.util.UUID
import kotlin.random.Random

/**
 * Builds a week's schedule out of the recipes already on the device.
 *
 * Pure and deterministic given a [Random] — no I/O, no Android, no repository — so the selection
 * rules can be unit-tested directly. [LocalMealPlanGenerator] supplies the candidates and persists
 * the result.
 *
 * This is a deliberately simple stand-in for the backend generator: it drops desserts, filters the
 * rest against the preferences that are checkable on-device, then deals them out round-robin so a
 * short collection repeats as late as possible. The user's go-to recipes that pass those filters are
 * dealt first and may come back up to [GO_TO_SERVINGS_PER_WEEK] times a week, whatever the variety
 * preference — eating them often is what the mark means.
 */
object MealPlanScheduler {

    /**
     * Words that, appearing in a recipe's tags or labels, satisfy a restriction.
     *
     * Matching is textual because the device has no nutrition data — an unavoidably loose test, and
     * the reason [schedule] falls back to unfiltered candidates rather than returning a short plan
     * when nothing matches. Vegan additionally satisfies vegetarian.
     */
    private val RESTRICTION_KEYWORDS: Map<DietaryRestriction, List<String>> = mapOf(
        DietaryRestriction.VEGAN to listOf("vegan"),
        DietaryRestriction.VEGETARIAN to listOf("vegetarian", "veggie", "vegan"),
        DietaryRestriction.LOW_CARB to listOf("low carb", "low-carb", "lowcarb", "keto"),
        DietaryRestriction.GLUTEN_FREE to listOf("gluten free", "gluten-free", "glutenfree"),
        DietaryRestriction.DAIRY_FREE to listOf("dairy free", "dairy-free", "dairyfree", "vegan"),
        DietaryRestriction.KETO to listOf("keto", "ketogenic"),
        DietaryRestriction.PALEO to listOf("paleo"),
    )

    /**
     * Tag/label names that mark a recipe as a dessert, which an auto-generated plan never schedules
     * — only mains and salads belong in a generated lunch/dinner slot. A user can still put a
     * dessert in a plan by hand.
     *
     * Matched against whole, case-insensitive tag/label names rather than substrings, so a tag
     * like "Sweet Potato" doesn't knock out a main course. Recipes carry no course field, so this
     * is the best signal available on-device.
     */
    private val DESSERT_KEYWORDS: Set<String> = setOf(
        "dessert", "desserts", "sweets", "cake", "cakes", "cookie", "cookies", "pastry", "pastries",
    )

    /**
     * Deals [candidates] into one [MealPlanDay] per planned day.
     *
     * @param goToRecipeIds the user's go-to recipes. Those among the eligible [candidates] fill the
     *   earliest slots, each up to [goToServingsPerRecipe] times; the rest of the week is dealt
     *   from the other recipes as usual. Empty leaves the schedule exactly as it would be without.
     * @param newDayId supplies the id for each generated day — a UUIDv7 factory in production.
     * @param random seeds the shuffle; inject a fixed seed to make a schedule reproducible.
     * @return one day per [MealPlanPreferences.planLengthDays], or an empty list if [candidates]
     *   holds no non-dessert recipe — a plan with no recipes to draw from cannot be scheduled.
     */
    fun schedule(
        preferences: MealPlanPreferences,
        candidates: List<RecipePreview>,
        goToRecipeIds: Set<UUID> = emptySet(),
        newDayId: () -> UUID,
        random: Random = Random.Default,
    ): List<MealPlanDay> {
        // Unlike the preference filters in [eligible], there is no fallback here: a plan of desserts
        // is worse than no plan.
        val meals = candidates.filterNot { it.isDessert() }
        if (meals.isEmpty()) return emptyList()

        val (goTo, others) = eligible(preferences, meals).partition { it.uuid in goToRecipeIds }
        // Go-to recipes skip the variety cut in [orderPool]: repeating them is the point of the mark.
        // Each round keeps the same order, so consecutive picks differ whenever there are two or more.
        val goToIds = goTo.shuffled(random).map { it.uuid }
        val goToPicks = List(goToServingsPerRecipe(preferences.planLengthDays)) { goToIds }
            .flatten()
            .toMutableList()
        // When every eligible recipe is a go-to, they also fill whatever their allowance leaves.
        val pool = orderPool(others.ifEmpty { goTo }, preferences.varietyPreference, random)
        val includeLunch = preferences.mealType == MealType.DINNER_AND_LUNCH

        // The first go-to pick not already on today's table, if any are left.
        fun nextGoTo(today: Set<UUID>): UUID? {
            val index = goToPicks.indexOfFirst { it !in today }
            return if (index < 0) null else goToPicks.removeAt(index)
        }

        // One cursor across the whole week so lunches and dinners draw from the same rotation and a
        // recipe is not served twice on the same day — unless the pool holds nothing else.
        var cursor = 0
        fun nextFromPool(today: Set<UUID>): UUID {
            val start = cursor
            var id: UUID
            do {
                id = pool[cursor++ % pool.size].uuid
            } while (id in today && cursor - start < pool.size)
            return id
        }

        return (0 until preferences.planLengthDays).map { dayIndex ->
            val today = mutableSetOf<UUID>()
            fun next(): UUID = (nextGoTo(today) ?: nextFromPool(today)).also { today += it }
            MealPlanDay(
                uuid = newDayId(),
                dayIndex = dayIndex,
                lunchRecipeId = if (includeLunch) next() else null,
                dinnerRecipeId = next(),
            )
        }
    }

    /**
     * Candidates that pass the preferences checkable on-device.
     *
     * Falls back to the unfiltered list when a filter would leave nothing: a plan built from
     * slightly-off recipes is more useful than an empty screen, and the user can swap meals out.
     */
    private fun eligible(
        preferences: MealPlanPreferences,
        candidates: List<RecipePreview>,
    ): List<RecipePreview> {
        val restrictions = preferences.dietaryRestrictions - DietaryRestriction.NONE

        val byDiet = if (restrictions.isEmpty()) {
            candidates
        } else {
            candidates.filter { recipe -> restrictions.all { recipe.satisfies(it) } }
        }.ifEmpty { candidates }

        val maxPrep = preferences.maxPrepTimeMinutes ?: return byDiet
        return byDiet.filter { it.prepTimeMinutes <= maxPrep }.ifEmpty { byDiet }
    }

    private fun RecipePreview.isDessert(): Boolean =
        (tags.map { it.displayName } + labels.map { it.displayName })
            .any { it.trim().lowercase() in DESSERT_KEYWORDS }

    private fun RecipePreview.satisfies(restriction: DietaryRestriction): Boolean {
        val keywords = RESTRICTION_KEYWORDS[restriction] ?: return true
        val haystack = (tags.map { it.displayName } + labels.map { it.displayName })
            .joinToString(" ") { it.lowercase() }
        return keywords.any { it in haystack }
    }

    /**
     * Orders the pool so that dealing round-robin produces the requested amount of repetition.
     *
     * [VarietyPreference.LOW] narrows the rotation to a handful of recipes so favourites come back
     * within the week; the others rotate through everything available.
     */
    private fun orderPool(
        eligible: List<RecipePreview>,
        variety: VarietyPreference,
        random: Random,
    ): List<RecipePreview> {
        val shuffled = eligible.shuffled(random)
        return when (variety) {
            VarietyPreference.HIGH, VarietyPreference.MEDIUM -> shuffled
            VarietyPreference.LOW -> shuffled.take(LOW_VARIETY_POOL_SIZE.coerceAtMost(shuffled.size))
        }
    }

    /** How many times one go-to recipe may appear in a plan: [GO_TO_SERVINGS_PER_WEEK], pro rata. */
    private fun goToServingsPerRecipe(planLengthDays: Int): Int =
        ((planLengthDays * GO_TO_SERVINGS_PER_WEEK + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK).coerceAtLeast(1)

    /** How many distinct recipes [VarietyPreference.LOW] rotates through. */
    private const val LOW_VARIETY_POOL_SIZE = 3

    /** How often a go-to recipe may come back within a week. */
    const val GO_TO_SERVINGS_PER_WEEK = 2

    private const val DAYS_PER_WEEK = 7
}
