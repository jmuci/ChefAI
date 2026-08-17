package com.tenmilelabs.chefai.search.ui.model

import androidx.annotation.StringRes
import com.tenmilelabs.chefai.R

/** The two browse sections on the Search tab, in render order. */
enum class SearchCategoryGroup(@param:StringRes val titleRes: Int) {
    MEAL(R.string.search_section_by_meal),
    POPULAR(R.string.search_section_popular_categories),
}

/**
 * A browse shortcut on the Search tab. Tapping one runs the ordinary recipe search with [query].
 *
 * [labelRes] is what the user reads and is localisable; [query] is the English term actually sent to
 * `GET /api/v1/recipes/search`, which matches against recipe titles, tags and labels — those are
 * stored in English, so the two must stay separate.
 *
 * Static on purpose: this is a hand-curated catalog, not a server-driven one. If categories ever
 * need to be personalised, they should come down the SDUI home-layout channel instead.
 */
enum class SearchCategory(
    val group: SearchCategoryGroup,
    @param:StringRes val labelRes: Int,
    val query: String,
) {
    BREAKFAST(SearchCategoryGroup.MEAL, R.string.search_category_breakfast, "breakfast"),
    LUNCH(SearchCategoryGroup.MEAL, R.string.search_category_lunch, "lunch"),
    SNACK(SearchCategoryGroup.MEAL, R.string.search_category_snack, "snack"),
    DINNER(SearchCategoryGroup.MEAL, R.string.search_category_dinner, "dinner"),
    DESSERT(SearchCategoryGroup.MEAL, R.string.search_category_dessert, "dessert"),
    KID_FRIENDLY(SearchCategoryGroup.MEAL, R.string.search_category_kid_friendly, "kid friendly"),

    LOW_CARB(SearchCategoryGroup.POPULAR, R.string.search_category_low_carb, "low carb"),
    SANDWICHES_WRAPS(SearchCategoryGroup.POPULAR, R.string.search_category_sandwiches_wraps, "sandwich"),
    QUICK_EASY(SearchCategoryGroup.POPULAR, R.string.search_category_quick_easy, "quick"),
    BUDGET_FRIENDLY(SearchCategoryGroup.POPULAR, R.string.search_category_budget_friendly, "budget"),
    AIR_FRYER(SearchCategoryGroup.POPULAR, R.string.search_category_air_fryer, "air fryer"),
    VEGETARIAN(SearchCategoryGroup.POPULAR, R.string.search_category_vegetarian, "vegetarian"),
    PROTEIN_PACKED(SearchCategoryGroup.POPULAR, R.string.search_category_protein_packed, "protein"),
    HEALTHY(SearchCategoryGroup.POPULAR, R.string.search_category_healthy, "healthy"),
    COOKIES(SearchCategoryGroup.POPULAR, R.string.search_category_cookies, "cookies"),
    COMFORT_FOOD(SearchCategoryGroup.POPULAR, R.string.search_category_comfort_food, "comfort food"),
    ;

    companion object {
        fun of(group: SearchCategoryGroup): List<SearchCategory> = entries.filter { it.group == group }
    }
}
