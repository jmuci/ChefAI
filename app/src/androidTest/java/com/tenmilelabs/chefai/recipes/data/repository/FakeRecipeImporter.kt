package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult
import com.tenmilelabs.chefai.recipes.domain.repository.RecipeImporter

/**
 * Fake implementation of [RecipeImporter] for instrumented tests — avoids real network I/O while
 * exercising the real navigation graph, ViewModel, and Room-backed draft persistence.
 *
 * Duplicated from the `test/` source set fake by design — see `docs/claude/gotchas.md` #15.
 */
class FakeRecipeImporter : RecipeImporter {

    var result: RecipeImportResult = RecipeImportResult.InvalidUrl
    var lastImportedUrl: String? = null
        private set
    var lastImportedHtml: String? = null
        private set

    override suspend fun import(url: String): RecipeImportResult {
        lastImportedUrl = url
        return result
    }

    override suspend fun importFromHtml(html: String, url: String): RecipeImportResult {
        lastImportedUrl = url
        lastImportedHtml = html
        return result
    }

    fun reset() {
        result = RecipeImportResult.InvalidUrl
        lastImportedUrl = null
        lastImportedHtml = null
    }
}
