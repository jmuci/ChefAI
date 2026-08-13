package com.tenmilelabs.chefai.recipes.domain.model

/** The outcome of importing a recipe from a URL — explicit and sealed rather than thrown. */
sealed interface RecipeImportResult {
    data class Success(val draft: RecipeDraft) : RecipeImportResult

    /**
     * Neither the HTTP fetch nor the off-screen browser could reach a recipe — the page is behind
     * an interactive bot check that only a human can clear. [url] is the normalised URL to open in
     * a browser the user can see.
     */
    data class NeedsBrowser(val url: String) : RecipeImportResult

    data object InvalidUrl : RecipeImportResult
    data object NoRecipeFound : RecipeImportResult
    data class NetworkError(val message: String) : RecipeImportResult
    data class ParseError(val message: String) : RecipeImportResult
}
