package com.tenmilelabs.chefai.ui.preview

import com.tenmilelabs.chefai.data.source.local.util.generateUuid7
import com.tenmilelabs.chefai.domain.model.Allergen
import com.tenmilelabs.chefai.domain.model.Ingredient
import com.tenmilelabs.chefai.domain.model.Label
import com.tenmilelabs.chefai.domain.model.RecipeStep
import com.tenmilelabs.chefai.domain.model.Tag
import com.tenmilelabs.chefai.domain.model.User

object SharedData {
    val user = User(
        uuid = generateUuid7(),
        displayName = "ChefAI Preview",
        email = "preview@chefai.app",
        avatarUrl = "https://i.pravatar.cc/150?u=a042581f4e29026704d"
    )
    val tagHealthy = Tag(generateUuid7(), "Healthy")
    val tagQuick = Tag(generateUuid7(), "Quick")
    val tagEasy = Tag(generateUuid7(), "Easy")
    val tagBrunch = Tag(generateUuid7(), "Brunch")
    val tagDelicious = Tag(generateUuid7(), "Delicious")
    val multipleTags = listOf(tagHealthy, tagQuick, tagBrunch, tagDelicious)

    val labelKeto = Label(generateUuid7(), "Keto")
    val labelVegan = Label(generateUuid7(), "Vegan")
    val labelMediterranean = Label(generateUuid7(), "Mediterranean")
    val labelVegetarian = Label(generateUuid7(), "Vegetarian")
    val labelDinnerParty = Label(generateUuid7(), "Dinner Party")
    val labelBatch = Label(generateUuid7(), "Batch Cook")
    val labelWeekNight = Label(generateUuid7(), "Week Night")
    val labelBreakfast = Label(generateUuid7(), "Breakfast")

    val multipleLabels = listOf(labelKeto, labelVegan, labelMediterranean, labelVegetarian, labelDinnerParty, labelBatch, labelWeekNight, labelBreakfast)

    val allergenGluten = Allergen(generateUuid7(), "Gluten")
    val allergenNuts = Allergen(generateUuid7(), "Nuts")
    val allergenPeanuts = Allergen(generateUuid7(), "Peanuts")
    val allergenSoy = Allergen(generateUuid7(), "Soy")
    val allergenWheat = Allergen(generateUuid7(), "Wheat")
    val allergenEggs = Allergen(generateUuid7(), "Eggs")
    val allergenDairy = Allergen(generateUuid7(), "Dairy")
    val allergenFish = Allergen(generateUuid7(), "Fish")

    val multiAllergens = listOf(allergenGluten, allergenNuts, allergenPeanuts, allergenSoy, allergenWheat, allergenEggs, allergenDairy, allergenFish)

    // Ingredients
    val ingredientSpaghetti = Ingredient(generateUuid7(), "Spaghetti", allergen = allergenWheat, sourcePrimary = null)
    val ingredientGuanciale = Ingredient(generateUuid7(), "Guanciale", allergen = null, sourcePrimary = null)
    val ingredientEggs = Ingredient(generateUuid7(), "Eggs", allergen = allergenEggs, sourcePrimary = null)
    val ingredientPecorino = Ingredient(generateUuid7(), "Pecorino Romano", allergen = allergenDairy, sourcePrimary = null)
    val ingredientBlackPepper = Ingredient(generateUuid7(), "Black Pepper", allergen = null, sourcePrimary = null)
    val ingredientChickenBreast = Ingredient(generateUuid7(), "Chicken Breast", allergen = null, sourcePrimary = null)
    val ingredientGarlic = Ingredient(generateUuid7(), "Garlic", allergen = null, sourcePrimary = null)
    val ingredientOliveOil = Ingredient(generateUuid7(), "Olive Oil", allergen = null, sourcePrimary = null)

    val carbonaraIngredients = listOf(ingredientSpaghetti, ingredientGuanciale, ingredientEggs, ingredientPecorino, ingredientBlackPepper)

    // Recipe Steps
    val carbonaraStep1 = RecipeStep(generateUuid7(), 1, "Cook spaghetti according to package directions.")
    val carbonaraStep2 = RecipeStep(generateUuid7(), 2, "While pasta is cooking, fry guanciale until crisp.")
    val carbonaraStep3 = RecipeStep(generateUuid7(), 3, "In a bowl, whisk eggs, pecorino romano, and a generous amount of black pepper.")
    val carbonaraStep4 = RecipeStep(generateUuid7(), 4, "Drain pasta, reserving some pasta water. Quickly mix hot pasta with the egg mixture and guanciale.")

    val carbonaraSteps = listOf(carbonaraStep1, carbonaraStep2, carbonaraStep3, carbonaraStep4)


}
