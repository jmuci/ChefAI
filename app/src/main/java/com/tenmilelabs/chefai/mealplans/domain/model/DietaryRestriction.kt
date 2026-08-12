package com.tenmilelabs.chefai.mealplans.domain.model

enum class DietaryRestriction(val emoji: String, val label: String) {
    NONE("\uD83C\uDF74", "No restrictions"),
    VEGAN("\uD83C\uDF31", "Vegan"),
    VEGETARIAN("\uD83E\uDD6C", "Vegetarian"),
    LOW_CARB("\uD83E\uDD69", "Low Carb"),
    GLUTEN_FREE("\uD83C\uDF3E", "Gluten-Free"),
    DAIRY_FREE("\uD83E\uDD5B", "Dairy-Free"),
    KETO("\uD83E\uDD51", "Keto"),
    PALEO("\uD83E\uDDB4", "Paleo"),
}
