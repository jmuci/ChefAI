package com.tenmilelabs.chefai.core.ui.preview

import com.tenmilelabs.chefai.core.data.local.util.generateUuid7
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.model.Tag

object PreviewData {
    const val BASE_IMG_URL = "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/"
        val pasta = "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/pasta-carbonara.jpeg"
    val recipePreview = RecipePreview(
        uuid = generateUuid7(),
        title = "Delicious Grilled Chicken",
        description = "A very tasty and easy to make grilled chicken recipe. Perfect for a summer barbecue. Follow the steps carefully for the best results.",
        imageUrlThumbnail = BASE_IMG_URL + "lem-chicken.jpeg",
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
        //imageUrlThumbnail = BASE_IMG_URL +"pasta-carbonara.jpeg",
        imageUrlThumbnail = pasta,
        prepTimeMinutes = 10,
        cookTimeMinutes = 25,
        servings = 6,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Pasta"), Tag(generateUuid7(), "Comfort Food")),
        labels = listOf(Label(generateUuid7(), "Weeknight")),
    )

    val recipePreviewNoImage = RecipePreview(
        uuid = generateUuid7(),
        title = "Brocoli Strogonoff",
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
        imageUrlThumbnail = BASE_IMG_URL + "chicken-med.jpeg",
        prepTimeMinutes = 15,
        cookTimeMinutes = 20,
        servings = 4,
        creatorId = SharedData.user.uuid,
        tags = emptyList(),
        labels = listOf(Label(generateUuid7(), "Mediterranean")),
    )

    val recipePreviewManyLabelsAndTags = RecipePreview(
        uuid = generateUuid7(),
        title = "Classic Beef Stew",
        description = "A simple, traditional beef stew that is perfect for a cold winter night.",
        imageUrlThumbnail = "https://images.unsplash.com/photo-1608500218861-01091cdc501e?q=80&w=987&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
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
        imageUrlThumbnail = "https://images.unsplash.com/photo-1598023696416-0193a0bcd302?q=80&w=1536&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
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
        imageUrlThumbnail = "https://images.unsplash.com/photo-1735315050688-010b5b548054?q=80&w=1585&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
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
        imageUrlThumbnail = "https://images.unsplash.com/photo-1499636136210-6f4ee915583e?q=80&w=1064&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
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
        imageUrlThumbnail = "https://plus.unsplash.com/premium_photo-1713089366140-814130d69933?q=80&w=1740&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
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
        //recipePreviewNoImage,
        recipePreviewNoTags,
        recipePreviewManyLabelsAndTags,
        recipePreview.copy(uuid = generateUuid7(), title = "Lemon Herb Roasted Chicken", imageUrlThumbnail = "https://media.istockphoto.com/id/1364436921/photo/lemon-butter-chicken-grilled-chicken-with-butter-lemon-and-garlic-lemon-chicken-dish.jpg?s=2048x2048&w=is&k=20&c=iKyK26uozMGiF48ITdaDOnxjhhkbh4JuDF3ZB2m4ow8="),
        recipePreviewLongTitle.copy(uuid = generateUuid7(), title = "Quick Shrimp Scampi"),
        recipePreview1,
        recipePreview2,
        recipePreview3,
        recipePreview4
    )
}