package com.tenmilelabs.chefai.core.ui.preview

import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.Allergen
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.model.SourceClassification
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.domain.model.User

object SharedData {
    val user = User(
        uuid = UuidV7Generator.newId(),
        displayName = "ChefAI Preview",
        email = "preview@chefai.app",
        avatarUrl = "https://i.pravatar.cc/150?u=a042581f4e29026704d"
    )
    val tagHealthy = Tag(UuidV7Generator.newId(), "Healthy")
    val tagQuick = Tag(UuidV7Generator.newId(), "Quick")
    val tagEasy = Tag(UuidV7Generator.newId(), "Easy")
    val tagBrunch = Tag(UuidV7Generator.newId(), "Brunch")
    val tagDelicious = Tag(UuidV7Generator.newId(), "Delicious")
    val multipleTags = listOf(tagHealthy, tagQuick, tagBrunch, tagDelicious)

    val labelKeto = Label(UuidV7Generator.newId(), "Keto")
    val labelVegan = Label(UuidV7Generator.newId(), "Vegan")
    val labelMediterranean = Label(UuidV7Generator.newId(), "Mediterranean")
    val labelVegetarian = Label(UuidV7Generator.newId(), "Vegetarian")
    val labelDinnerParty = Label(UuidV7Generator.newId(), "Dinner Party")
    val labelBatch = Label(UuidV7Generator.newId(), "Batch Cook")
    val labelWeekNight = Label(UuidV7Generator.newId(), "Week Night")
    val labelBreakfast = Label(UuidV7Generator.newId(), "Breakfast")

    val multipleLabels = listOf(labelKeto, labelVegan, labelMediterranean, labelVegetarian, labelDinnerParty, labelBatch, labelWeekNight, labelBreakfast)

    val allergenGluten = Allergen(UuidV7Generator.newId(), "Gluten")
    val allergenNuts = Allergen(UuidV7Generator.newId(), "Nuts")
    val allergenPeanuts = Allergen(UuidV7Generator.newId(), "Peanuts")
    val allergenSoy = Allergen(UuidV7Generator.newId(), "Soy")
    val allergenWheat = Allergen(UuidV7Generator.newId(), "Wheat")
    val allergenEggs = Allergen(UuidV7Generator.newId(), "Eggs")
    val allergenDairy = Allergen(UuidV7Generator.newId(), "Dairy")
    val allergenFish = Allergen(UuidV7Generator.newId(), "Fish")

    val multiAllergens = listOf(allergenGluten, allergenNuts, allergenPeanuts, allergenSoy, allergenWheat, allergenEggs, allergenDairy, allergenFish)

    val srcClassPork = SourceClassification(UuidV7Generator.newId(), "Animal", "Pork")
    val srcClassBeef = SourceClassification(UuidV7Generator.newId(), "Animal", "Beef")
    val srcClassChicken = SourceClassification(UuidV7Generator.newId(), "Animal", "Chicken")
    val srcClassFish = SourceClassification(UuidV7Generator.newId(), "Animal", "Fish")
    val srscClassEgg = SourceClassification(UuidV7Generator.newId(), "Animal", "Egg")
    val srcClassShrimp = SourceClassification(UuidV7Generator.newId(), "Animal", "Shrimp")
    val scrClassGrain = SourceClassification(UuidV7Generator.newId(), "Vegetable", "Grain")
    val scrClassLegume = SourceClassification(UuidV7Generator.newId(), "Vegetable", "Legume")
    val srcClassSpice = SourceClassification(UuidV7Generator.newId(), "Vegetable", "Spice")




    // Ingredients
    val ingredientSpaghetti = RecipeIngredient(UuidV7Generator.newId(), "Spaghetti", quantity = 500.0, unit= "gr", allergenName = allergenWheat.displayName, srcCategory = scrClassGrain.category, srcSubcategory = scrClassGrain.subcategory)
    val ingredientGuanciale = RecipeIngredient(UuidV7Generator.newId(), "Guanciale", quantity = 200.0, unit= "gr", allergenName = null, srcCategory = srcClassPork.category, srcSubcategory = srcClassPork.subcategory)
    val ingredientEggs = RecipeIngredient(UuidV7Generator.newId(), "Eggs", quantity = 2.0, unit= "units", allergenName = allergenEggs.displayName, srcCategory = srscClassEgg.category, srcSubcategory = srscClassEgg.subcategory)
    val ingredientPecorino = RecipeIngredient(UuidV7Generator.newId(), "Pecorino Romano", quantity = 250.0, unit= "gr", allergenName = allergenDairy.displayName, srcCategory = scrClassGrain.category, srcSubcategory = scrClassGrain.subcategory)
    val ingredientBlackPepper = RecipeIngredient(UuidV7Generator.newId(), "Black Pepper", quantity = 50.0, unit= "gr", allergenName = null, srcCategory = srcClassSpice.category, srcSubcategory = srcClassSpice.subcategory)
    val ingredientChickenBreast = RecipeIngredient(UuidV7Generator.newId(), "Chicken Breast", quantity = 500.0, unit= "gr", allergenName = null, srcCategory = scrClassGrain.category, srcSubcategory = scrClassGrain.subcategory)
    val ingredientGarlic = RecipeIngredient(UuidV7Generator.newId(), "Garlic", quantity = 500.0, unit= "gr", allergenName = allergenWheat.displayName, srcCategory = scrClassGrain.category, srcSubcategory = scrClassGrain.subcategory)
    val ingredientOliveOil = RecipeIngredient(UuidV7Generator.newId(), "Olive Oil", quantity = 500.0, unit= "gr", allergenName = allergenWheat.displayName, srcCategory = scrClassGrain.category, srcSubcategory = scrClassGrain.subcategory)

    val carbonaraIngredients = listOf(ingredientSpaghetti, ingredientGuanciale, ingredientEggs, ingredientPecorino, ingredientBlackPepper)

    // Recipe Steps
    val carbonaraStep1 = RecipeStep(UuidV7Generator.newId(), 1, "Cook spaghetti according to package directions.")
    val carbonaraStep2 = RecipeStep(UuidV7Generator.newId(), 2, "While pasta is cooking, fry guanciale until crisp.")
    val carbonaraStep3 = RecipeStep(UuidV7Generator.newId(), 3, "In a bowl, whisk eggs, pecorino romano, and a generous amount of black pepper.")
    val carbonaraStep4 = RecipeStep(UuidV7Generator.newId(), 4, "Drain pasta, reserving some pasta water. Quickly mix hot pasta with the egg mixture and guanciale.")

    val carbonaraSteps = listOf(carbonaraStep1, carbonaraStep2, carbonaraStep3, carbonaraStep4)


}
