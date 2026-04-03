package com.tenmilelabs.chefai.mealplans.domain.model

enum class MealType(val emoji: String, val label: String) {
    DINNER("\uD83C\uDF7D\uFE0F", "Dinners only"),
    DINNER_AND_LUNCH("\uD83C\uDF7D\uFE0F\uD83E\uDD57", "Dinners + Lunches"),
}
