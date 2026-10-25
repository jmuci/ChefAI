package com.tenmilelabs.chefai.recipes.ui

import androidx.annotation.StringRes
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview

/** Total (prep + cook) time, in minutes, at or under which a recipe counts as [RecipeFilter.QUICK_EASY]. */
const val QUICK_EASY_MAX_TOTAL_MINUTES = 20

/**
 * The single-select filter chips above the Recipes tab's grid (04).
 *
 * Filters are a purely local predicate over the user's own collection — they never hit the search
 * pipeline (`RecipeSearchRepository`), which queries the backend's public catalog instead.
 */
enum class RecipeFilter(@StringRes val labelRes: Int) {
    ALL(R.string.recipe_filter_all),

    /**
     * Total time of at most [QUICK_EASY_MAX_TOTAL_MINUTES]. A recipe with no time recorded at all
     * (both fields `0`, common for imports) is excluded rather than treated as instant.
     */
    QUICK_EASY(R.string.search_category_quick_easy),

    /** Any tag or label whose name mentions "dessert" ("Dessert", "Desserts", "Easy dessert"…). */
    DESSERT(R.string.search_category_dessert),
    ;

    /** Whether [recipe] belongs under this filter. */
    fun matches(recipe: RecipePreview): Boolean = when (this) {
        ALL -> true
        QUICK_EASY -> {
            val total = recipe.prepTimeMinutes + recipe.cookTimeMinutes
            total in 1..QUICK_EASY_MAX_TOTAL_MINUTES
        }
        DESSERT -> (recipe.tags.map { it.displayName } + recipe.labels.map { it.displayName })
            .any { it.contains(DESSERT_KEYWORD, ignoreCase = true) }
    }

    private companion object {
        const val DESSERT_KEYWORD = "dessert"
    }
}
