package com.tenmilelabs.chefai.recipes.domain.repository

import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult

/** Fetches a third-party recipe page and extracts a [RecipeImportResult.Success] draft from it. */
interface RecipeImporter {

    /**
     * Fetches [url] and extracts a recipe from it, falling back to a browser engine for pages that
     * refuse plain HTTP clients. Returns [RecipeImportResult.NeedsBrowser] when the page can only
     * be reached by a browser the user can interact with.
     */
    suspend fun import(url: String): RecipeImportResult

    /**
     * Extracts a recipe from HTML that has already been fetched — the hand-back from the visible
     * browser fallback, which does its own loading. [url] is recorded on the draft as the source
     * and is not fetched.
     */
    suspend fun importFromHtml(html: String, url: String): RecipeImportResult
}
