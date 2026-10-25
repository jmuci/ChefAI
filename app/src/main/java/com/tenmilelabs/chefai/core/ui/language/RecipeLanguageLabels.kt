package com.tenmilelabs.chefai.core.ui.language

import androidx.annotation.StringRes
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipeLanguage

/**
 * Each language's name in its own language ("Español", not "Spanish"), so whoever reads a recipe
 * in it recognises it at a glance whatever language the app itself is in.
 */
@get:StringRes
val RecipeLanguage.labelRes: Int
    get() = when (this) {
        RecipeLanguage.ENGLISH -> R.string.recipe_language_en
        RecipeLanguage.SPANISH -> R.string.recipe_language_es
        RecipeLanguage.FRENCH -> R.string.recipe_language_fr
        RecipeLanguage.ITALIAN -> R.string.recipe_language_it
        RecipeLanguage.PORTUGUESE -> R.string.recipe_language_pt
        RecipeLanguage.GERMAN -> R.string.recipe_language_de
    }
