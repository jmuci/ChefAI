package com.tenmilelabs.chefai.mealplans.domain.model

enum class MealType(val emoji: String, val label: String) {
    DINNER("\uD83C\uDF7D\uFE0F", "Dinners only"),
    DINNER_AND_LUNCH("\uD83C\uDF7D\uFE0F\uD83E\uDD57", "Dinners + Lunches"),
    ;

    /** The slots a plan of this type fills each day — an empty one is a gap to offer "Add recipe" in. */
    val slots: Set<MealSlot>
        get() = when (this) {
            DINNER -> setOf(MealSlot.DINNER)
            DINNER_AND_LUNCH -> setOf(MealSlot.LUNCH, MealSlot.DINNER)
        }
}
