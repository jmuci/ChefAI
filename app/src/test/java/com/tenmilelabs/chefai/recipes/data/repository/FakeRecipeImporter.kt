package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult
import com.tenmilelabs.chefai.recipes.domain.repository.RecipeImporter
import kotlinx.coroutines.CompletableDeferred

class FakeRecipeImporter : RecipeImporter {

    var result: RecipeImportResult = RecipeImportResult.InvalidUrl
    var callCount: Int = 0
        private set
    var lastImportedUrl: String? = null
        private set

    /** When set, [import] suspends until this completes — lets tests assert in-flight behaviour. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun import(url: String): RecipeImportResult {
        callCount++
        lastImportedUrl = url
        gate?.await()
        return result
    }
}
