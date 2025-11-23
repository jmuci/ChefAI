package com.tenmilelabs.chefai.core.ui.preview

import com.tenmilelabs.chefai.core.data.local.util.generateUuid7
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.model.Tag

object PreviewData {
    val recipePreview = RecipePreview(
        uuid = generateUuid7(),
        title = "Delicious Grilled Chicken",
        description = "A very tasty and easy to make grilled chicken recipe. Perfect for a summer barbecue. Follow the steps carefully for the best results.",
        imageUrlThumbnail = "https://images.pexels.com/photos/106343/pexels-photo-106343.jpeg",
        prepTimeMinutes = 15,
        cookTimeMinutes = 20,
        servings = 4,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Healthy"), Tag(generateUuid7(), "Quick")),
        labels = listOf(Label(generateUuid7(), "Dinner")),
    )

    val recipePreviewLongTitle = RecipePreview(
        uuid = generateUuid7(),
        title = "Spicy Tuscan Sausage & Kale Pasta with Sun-Dried Tomatoes and a Creamy Parmesan Sauce",
        description = "A hearty and comforting pasta dish that comes together in under 30 minutes. Perfect for a weeknight family dinner.",
        imageUrlThumbnail = "https://www.istockphoto.com/photo/homemade-cavatelli-pasta-dinner-gm1355330441-429847611?utm_source=unsplash&utm_medium=affiliate&utm_campaign=srp_photos_bottom&utm_content=https%3A%2F%2Funsplash.com%2Fs%2Fphotos%2Fsausage-pasta-kale&utm_term=sausage+pasta+kale%3A%3Abottom-affiliates-3col%3Aexperiment",
        prepTimeMinutes = 10,
        cookTimeMinutes = 25,
        servings = 6,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Pasta"), Tag(generateUuid7(), "Comfort Food")),
        labels = listOf(Label(generateUuid7(), "Weeknight")),
    )

    val recipePreviewNoImage = RecipePreview(
        uuid = generateUuid7(),
        title = "Classic Beef Stew",
        description = "A simple, traditional beef stew. This recipe is a placeholder and does not have an image, used for testing fallback UI.",
        imageUrlThumbnail = "", // No image
        prepTimeMinutes = 20,
        cookTimeMinutes = 180,
        servings = 8,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Slow Cook"), Tag(generateUuid7(), "Beef")),
        labels = listOf(Label(generateUuid7(), "Batch Cook")),
    )

    val recipePreviewNoTags = RecipePreview(
        uuid = generateUuid7(),
        title = "Mediterranean Grilled Chicken",
        description = "A light and flavorful grilled chicken recipe with classic Mediterranean herbs and a lemon-garlic marinade.",
        imageUrlThumbnail = "https://www.themediterraneandish.com/wp-content/uploads/2015/05/mediterranean-grilled-chicken-recipe-13.jpg",
        prepTimeMinutes = 15,
        cookTimeMinutes = 20,
        servings = 4,
        creatorId = SharedData.user.uuid,
        tags = emptyList(),
        labels = listOf(Label(generateUuid7(), "Mediterranean")),
    )

    val recipePreviewManyLabelsAndTags = RecipePreview(
        uuid = generateUuid7(),
        title = "Mediterranean Grilled Chicken",
        description = "A light and flavorful grilled chicken recipe with classic Mediterranean herbs and a lemon-garlic marinade.",
        imageUrlThumbnail = "https://www.themediterraneandish.com/wp-content/uploads/2015/05/mediterranean-grilled-chicken-recipe-13.jpg",
        prepTimeMinutes = 15,
        cookTimeMinutes = 20,
        servings = 4,
        creatorId = SharedData.user.uuid,
        tags = SharedData.multipleTags,
        labels = SharedData.multipleLabels,
    )

    val recipePreview1 = recipePreview.copy(
        uuid = generateUuid7(),
        title = "Classic Margherita Pizza",
        description = "Authentic Italian pizza with fresh mozzarella, basil, and tomato sauce on a crispy thin crust.",
        prepTimeMinutes = 20,
        cookTimeMinutes = 15,
        servings = 2,
        labels = listOf(
            recipePreview.labels.first().copy(displayName = "Italian"),
            recipePreview.labels.last().copy(displayName = "Vegetarian")
        )
    )

    val recipePreview2 = recipePreview.copy(
        uuid = generateUuid7(),
        title = "Grilled Salmon Teriyaki",
        description = "Perfectly grilled salmon glazed with homemade teriyaki sauce, served with steamed vegetables.",
        prepTimeMinutes = 10,
        cookTimeMinutes = 15,
        servings = 4,
        labels = listOf(
            recipePreview.labels.first().copy(displayName = "Healthy"),
            recipePreview.labels.last().copy(displayName = "Asian")
        )
    )
    val recipePreview3 = recipePreview.copy(
        uuid = generateUuid7(),
        title = "Chocolate Chip Cookies",
        description = "Soft and chewy homemade chocolate chip cookies that are perfect for any occasion.",
        prepTimeMinutes = 15,
        cookTimeMinutes = 12,
        servings = 24,
        labels = listOf(
            recipePreview.labels.first().copy(displayName = "Dessert"),
            recipePreview.labels.last().copy(displayName = "Baking")
        )
    )
    val recipePreview4 = recipePreview.copy(
        uuid = generateUuid7(),
        title = "Thai Green Curry",
        description = "Aromatic and spicy Thai curry with coconut milk, vegetables, and your choice of protein.",
        prepTimeMinutes = 15,
        cookTimeMinutes = 25,
        servings = 4,
        labels = listOf(
            recipePreview.labels.first().copy(displayName = "Spicy"),
            recipePreview.labels.last().copy(displayName = "Asian")
        )
    )

    val recipePreviewList = listOf(
        recipePreview,
        recipePreviewLongTitle,
        recipePreviewNoImage,
        recipePreviewNoTags,
        recipePreview.copy(uuid = generateUuid7(), title = "Lemon Herb Roasted Chicken"),
        recipePreviewLongTitle.copy(uuid = generateUuid7(), title = "Quick Shrimp Scampi"),
        recipePreview.copy(uuid = generateUuid7(), title = "Vegetarian Chili"),
        recipePreview1,
        recipePreview2,
        recipePreview3,
        recipePreview4
    )
}