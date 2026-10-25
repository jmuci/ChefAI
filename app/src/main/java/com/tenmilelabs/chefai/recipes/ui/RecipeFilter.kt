package com.tenmilelabs.chefai.recipes.ui

import androidx.annotation.StringRes
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.QUICK_EASY_MAX_TOTAL_MINUTES
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import java.util.UUID

/**
 * The single-select filter chips above the Recipes tab's grid (04).
 *
 * Filters are a purely local predicate over the user's own collection — they never hit the search
 * pipeline (`RecipeSearchRepository`), which queries the backend's public catalog instead.
 */
enum class RecipeFilter(
    @StringRes val labelRes: Int,
    @StringRes val emptyTitleRes: Int = R.string.no_filtered_recipes_title,
    @StringRes val emptySubtitleRes: Int = R.string.no_filtered_recipes_subtitle,
) {
    ALL(R.string.recipe_filter_all),

    /**
     * Total time of at most [QUICK_EASY_MAX_TOTAL_MINUTES]. A recipe with no time recorded at all
     * (both fields `0`, common for imports) is excluded rather than treated as instant.
     */
    QUICK_EASY(R.string.search_category_quick_easy),

    /** Any tag or label whose name mentions "dessert" ("Dessert", "Desserts", "Easy dessert"…). */
    DESSERT(R.string.search_category_dessert),

    /**
     * Recipes cooked at least once, per the durable history behind `recipe_cook_events` — so it
     * survives the meal plan it was cooked in being deleted. The one filter whose predicate needs
     * data that is not on [RecipePreview], hence `cookedRecipeIds` on [matches].
     */
    COOKED_BEFORE(
        R.string.recipe_filter_cooked_before,
        R.string.no_cooked_recipes_title,
        R.string.no_cooked_recipes_subtitle,
    ),
    ;

    /**
     * Whether [recipe] belongs under this filter. [cookedRecipeIds] is the set of recipes the user
     * has ever cooked; only [COOKED_BEFORE] reads it.
     */
    fun matches(recipe: RecipePreview, cookedRecipeIds: Set<UUID> = emptySet()): Boolean = when (this) {
        ALL -> true
        QUICK_EASY -> {
            val total = recipe.prepTimeMinutes + recipe.cookTimeMinutes
            total in 1..QUICK_EASY_MAX_TOTAL_MINUTES
        }
        DESSERT -> (recipe.tags.map { it.displayName } + recipe.labels.map { it.displayName })
            .any { it.contains(DESSERT_KEYWORD, ignoreCase = true) }
        COOKED_BEFORE -> recipe.uuid in cookedRecipeIds
    }

    private companion object {
        const val DESSERT_KEYWORD = "dessert"
    }
}
