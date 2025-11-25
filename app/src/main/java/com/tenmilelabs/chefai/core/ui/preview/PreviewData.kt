package com.tenmilelabs.chefai.core.ui.preview

import com.tenmilelabs.chefai.core.data.local.util.generateUuid7
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.model.Tag

object PreviewData {
    const val BASE_IMG_URL =
        "https://raw.githubusercontent.com/jmuci/ChATestAPI/refs/heads/main/statics/thumbnails/lowres/"
    val grilledChickenRecipe = RecipePreview(
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

    val tuscanSausagePasta = RecipePreview(
        uuid = generateUuid7(),
        title = "Spicy Tuscan Sausage & Kale Pasta with Sun-Dried Tomatoes and a Creamy Parmesan Sauce",
        description = "A hearty and comforting pasta dish that comes together in under 30 minutes. Perfect for a weeknight family dinner.",
        imageUrlThumbnail = BASE_IMG_URL + "pasta-carbonara.jpeg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 25,
        servings = 6,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Pasta"), Tag(generateUuid7(), "Comfort Food")),
        labels = listOf(Label(generateUuid7(), "Weeknight")),
    )


    val carbonaraPasta = RecipePreview(
        uuid = generateUuid7(),
        title = "Traditional Carbonara Pasta",
        description = "A classic Italian pasta dish made with eggs, cheese, pancetta, and pepper.",
        imageUrlThumbnail = BASE_IMG_URL + "pasta-carbonara2.jpeg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 25,
        servings = 6,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Pasta"), Tag(generateUuid7(), "Comfort Food")),
        labels = listOf(Label(generateUuid7(), "Weeknight")),
    )

    val beefStewNoPhoto = RecipePreview(
        uuid = generateUuid7(),
        title = "Brocoli Strogonoff",
        description = " does not have an image",
        imageUrlThumbnail = "", // No image
        prepTimeMinutes = 20,
        cookTimeMinutes = 180,
        servings = 8,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Slow Cook"), Tag(generateUuid7(), "Beef")),
        labels = listOf(Label(generateUuid7(), "Batch Cook")),
    )

    val medGrilledChickenRecipeNoTags = RecipePreview(
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

    val beefStewManyLabelsAndTags = RecipePreview(
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

    val pizzaRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Classic Margherita Pizza",
        description = "Authentic Italian pizza with fresh mozzarella, basil, and tomato sauce on a crispy thin crust.",
        imageUrlThumbnail = BASE_IMG_URL + "pizza-marg .jpeg",
        prepTimeMinutes = 20,
        cookTimeMinutes = 15,
        servings = 2,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Italian"), Tag(generateUuid7(), "Dinner")),
        labels = listOf(
            grilledChickenRecipe.labels.first().copy(displayName = "Italian"),
            grilledChickenRecipe.labels.last().copy(displayName = "Vegetarian")
        )
    )

    val lasagnaRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Lasagna Bolognese",
        description = "Authentic lasagna with rich meat sauce, creamy béchamel, and melted cheese.",
        imageUrlThumbnail = BASE_IMG_URL + "lasagne.jpeg",
        prepTimeMinutes = 20,
        cookTimeMinutes = 50,
        servings = 2,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Italian"), Tag(generateUuid7(), "Dinner")),
        labels = listOf(
            grilledChickenRecipe.labels.first().copy(displayName = "Italian"),
            grilledChickenRecipe.labels.last().copy(displayName = "Vegetarian")
        )
    )


    val salmonTerRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Grilled Salmon Teriyaki",
        description = "Perfectly grilled salmon glazed with homemade teriyaki sauce, served with steamed vegetables.",
        imageUrlThumbnail = BASE_IMG_URL + "salmon-ter.jpeg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 15,
        creatorId = SharedData.user.uuid,
        servings = 4,
        tags = listOf(
            Tag(generateUuid7(), "Healthy"),
            Tag(generateUuid7(), "Dinner"),
            Tag(generateUuid7(), "Fish")
        ),
        labels = listOf(
            grilledChickenRecipe.labels.first().copy(displayName = "Healthy"),
            grilledChickenRecipe.labels.last().copy(displayName = "Asian")
        )
    )

    val sushiRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Sushi Nigiris",
        description = "Sushi nigiris are a classic Japanese dish made with fresh fish and vinegared rice.",
        imageUrlThumbnail = BASE_IMG_URL + "sushi.jpeg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 15,
        creatorId = SharedData.user.uuid,
        servings = 4,
        tags = listOf(
            Tag(generateUuid7(), "Healthy"),
            Tag(generateUuid7(), "Dinner"),
            Tag(generateUuid7(), "Fish")
        ),
        labels = listOf(
            grilledChickenRecipe.labels.first().copy(displayName = "Healthy"),
            grilledChickenRecipe.labels.last().copy(displayName = "Asian")
        )
    )

    val shrimpCevicheRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Shrimp Ceviche",
        description = "Delicious and refreshing shrimp ceviche with fresh lime juice, tomatoes, and cilantro.",
        imageUrlThumbnail = BASE_IMG_URL + "shrimp-ceviche.jpeg",
        prepTimeMinutes = 10,
        cookTimeMinutes = 15,
        creatorId = SharedData.user.uuid,
        servings = 4,
        tags = listOf(
            Tag(generateUuid7(), "Healthy"),
            Tag(generateUuid7(), "Dinner"),
            Tag(generateUuid7(), "Fish")
        ),
        labels = listOf(
            grilledChickenRecipe.labels.first().copy(displayName = "Healthy"),
            grilledChickenRecipe.labels.last().copy(displayName = "Asian")
        )
    )

    val codWithLemonRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Battered Cod with Lemon",
        description = "Our take on the classic battered cod with a twist of lemon and a side of chips.",
        imageUrlThumbnail = BASE_IMG_URL + "shrimp_ceviche.jpeg",
        prepTimeMinutes = 20,
        cookTimeMinutes = 25,
        creatorId = SharedData.user.uuid,
        servings = 4,
        tags = listOf(
            Tag(generateUuid7(), "Pub Food"),
            Tag(generateUuid7(), "Dinner"),
            Tag(generateUuid7(), "Fish")
        ),
        labels = listOf(
            grilledChickenRecipe.labels.last().copy(displayName = "British")
        )
    )

    val cookiesRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Chocolate Chip Cookies",
        description = "Soft and chewy homemade chocolate chip cookies that are perfect for any occasion.",
        imageUrlThumbnail = "https://images.unsplash.com/photo-1499636136210-6f4ee915583e?q=80&w=1064&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
        prepTimeMinutes = 15,
        cookTimeMinutes = 12,
        servings = 24,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Dessert"), Tag(generateUuid7(), "Baking")),
        labels = listOf(
            grilledChickenRecipe.labels.first().copy(displayName = "Dessert"),
            grilledChickenRecipe.labels.last().copy(displayName = "Baking")
        )
    )

    val spaceCookiesRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Ghiradeli Chocolate Chip Cookies",
        description = "Extra inter chocolatey and chewy cookies that are perfect for any occasion.",
        imageUrlThumbnail = BASE_IMG_URL + "cookies.jpeg",
        prepTimeMinutes = 15,
        cookTimeMinutes = 12,
        servings = 24,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Dessert"), Tag(generateUuid7(), "Baking")),
        labels = listOf(
            grilledChickenRecipe.labels.first().copy(displayName = "Dessert"),
            grilledChickenRecipe.labels.last().copy(displayName = "Baking")
        )
    )
    val cheeseCakeRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Blue Berry Cheesecake",
        description = "Soft and delicious cheesecake with a layer of fresh berries on top.",
        imageUrlThumbnail = BASE_IMG_URL + "cheesecake.jpeg",
        prepTimeMinutes = 15,
        cookTimeMinutes = 12,
        servings = 24,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Dessert"), Tag(generateUuid7(), "Baking")),
        labels = listOf(
            grilledChickenRecipe.labels.first().copy(displayName = "Dessert"),
            grilledChickenRecipe.labels.last().copy(displayName = "Baking")
        )
    )

    val thaiGreenCurryRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Thai Green Curry",
        description = "Aromatic and spicy Thai curry with coconut milk, vegetables, and your choice of protein.",
        imageUrlThumbnail = "https://plus.unsplash.com/premium_photo-1713089366140-814130d69933?q=80&w=1740&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
        prepTimeMinutes = 15,
        cookTimeMinutes = 25,
        servings = 4,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Dinner"), Tag(generateUuid7(), "Thai")),
        labels = listOf(
            grilledChickenRecipe.labels.first().copy(displayName = "Spicy"),
            grilledChickenRecipe.labels.last().copy(displayName = "Asian")
        )
    )

    val paellaRecipe = RecipePreview(
        uuid = generateUuid7(),
        title = "Valencian Paella",
        description = "Original Valencian paella with rabbit, chicken, and beans. A traditional Spanish dish.",
        imageUrlThumbnail = BASE_IMG_URL + "paella-closeup.jpeg",
        prepTimeMinutes = 15,
        cookTimeMinutes = 25,
        servings = 4,
        creatorId = SharedData.user.uuid,
        tags = listOf(Tag(generateUuid7(), "Dinner"), Tag(generateUuid7(), "Thai")),
        labels = listOf(
            grilledChickenRecipe.labels.first().copy(displayName = "Spicy"),
            grilledChickenRecipe.labels.last().copy(displayName = "Asian")
        )
    )
    val dessertsPreviewList = listOf(cookiesRecipe, cheeseCakeRecipe, spaceCookiesRecipe)
    val italianPreviewList = listOf(carbonaraPasta, pizzaRecipe, lasagnaRecipe, tuscanSausagePasta)
    val seafoodPreviewList =
        listOf(salmonTerRecipe, sushiRecipe, shrimpCevicheRecipe, codWithLemonRecipe)

    val lowCarbsPreviewList = listOf(
        grilledChickenRecipe,
        salmonTerRecipe,
        beefStewManyLabelsAndTags,
        shrimpCevicheRecipe
    )

    val forYouPreview = listOf(
        paellaRecipe,
        grilledChickenRecipe,
        thaiGreenCurryRecipe,
        pizzaRecipe,
        beefStewManyLabelsAndTags,
        thaiGreenCurryRecipe,
        cheeseCakeRecipe
    )

    val recipePreviewList = listOf(
        grilledChickenRecipe,
        beefStewNoPhoto,
        medGrilledChickenRecipeNoTags,
        beefStewManyLabelsAndTags,
        grilledChickenRecipe.copy(
            uuid = generateUuid7(),
            title = "Lemon Herb Roasted Chicken",
            imageUrlThumbnail = "https://media.istockphoto.com/id/1364436921/photo/lemon-butter-chicken-grilled-chicken-with-butter-lemon-and-garlic-lemon-chicken-dish.jpg?s=2048x2048&w=is&k=20&c=iKyK26uozMGiF48ITdaDOnxjhhkbh4JuDF3ZB2m4ow8="
        ),
        tuscanSausagePasta.copy(uuid = generateUuid7(), title = "Quick Shrimp Scampi"),
        pizzaRecipe,
        salmonTerRecipe,
        cookiesRecipe,
        thaiGreenCurryRecipe
    )
}