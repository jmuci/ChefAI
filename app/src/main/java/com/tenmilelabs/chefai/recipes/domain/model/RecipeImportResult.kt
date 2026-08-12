package com.tenmilelabs.chefai.recipes.domain.model

/** The outcome of importing a recipe from a URL — explicit and sealed rather than thrown. */
sealed interface RecipeImportResult {
    data class Success(val draft: RecipeDraft) : RecipeImportResult
    data object InvalidUrl : RecipeImportResult
    data object NoRecipeFound : RecipeImportResult
    data class NetworkError(val message: String) : RecipeImportResult
    data class ParseError(val message: String) : RecipeImportResult
}
