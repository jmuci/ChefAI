package com.tenmilelabs.chefai.recipes.domain.repository

import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult

/** Fetches a third-party recipe page and extracts a [RecipeImportResult.Success] draft from it. */
interface RecipeImporter {
    suspend fun import(url: String): RecipeImportResult
}
