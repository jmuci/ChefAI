package com.tenmilelabs.chefai.mealplans.domain.model

enum class RecipeSource(val emoji: String, val label: String) {
    COLLECTION_ONLY("\uD83D\uDCDA", "My collection only"),
    INCLUDE_PUBLIC("\uD83C\uDF0D", "Include public recipes"),
}
