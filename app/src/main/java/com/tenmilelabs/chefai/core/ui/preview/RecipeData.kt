package com.tenmilelabs.chefai.core.ui.preview

import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.domain.model.Recipe

object RecipeData {
    val recipe =
        Recipe(
            uuid = UuidV7Generator.newId(),
            title = "Spaghetti Carbonara",
            description = "A very tasty and easy to make grilled chicken recipe. Perfect for a summer barbecue. Follow the steps carefully for the best results.",
            imageUrl = "https://via.placeholder.com/200",
            imageUrlThumbnail = "https://via.placeholder.com/200",
            prepTimeMinutes = 15,
            cookTimeMinutes = 20,
            servings = 4,
            creator = SharedData.user,
            recipeExternalUrl = "https://example.com/grilled-chicken",
            ingredients = SharedData.carbonaraIngredients,
            steps = SharedData.carbonaraSteps,
            tags = listOf(SharedData.tagQuick, SharedData.tagEasy),
            labels = listOf(SharedData.labelVegetarian, SharedData.labelMediterranean),
            updatedAt = System.currentTimeMillis()
        )
}