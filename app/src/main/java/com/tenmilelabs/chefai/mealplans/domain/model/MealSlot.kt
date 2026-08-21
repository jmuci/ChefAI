package com.tenmilelabs.chefai.mealplans.domain.model

/**
 * Which meal of a [MealPlanDay] a recipe fills.
 *
 * A day holds at most one lunch and one dinner, so the slot plus the day identifies a single
 * planned meal — the unit "mark as cooked" tracks. The same recipe planned twice in a week is two
 * slots, and each is cooked independently.
 */
enum class MealSlot(val label: String) {
    LUNCH("Lunch"),
    DINNER("Dinner"),
}
