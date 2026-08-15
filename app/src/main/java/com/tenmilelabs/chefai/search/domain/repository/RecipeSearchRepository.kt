package com.tenmilelabs.chefai.search.domain.repository

import com.tenmilelabs.chefai.core.domain.model.RecipePreview

/** Where [RecipeSearchOutcome.results] actually came from — the UI uses this to flag degraded results. */
enum class RecipeSearchSource { REMOTE, LOCAL_FALLBACK }

data class RecipeSearchOutcome(
    val results: List<RecipePreview>,
    val hasMore: Boolean,
    val source: RecipeSearchSource,
)

interface RecipeSearchRepository {
    /**
     * Searches recipes by title, tag, and label. Falls back to an on-device `LIKE` scan — see
     * [RecipeSearchSource.LOCAL_FALLBACK] — for anonymous sessions (no JWT) and whenever the
     * network call fails; that fallback is a deliberate degradation, not a second source of
     * truth, so it never carries `hasMore = true`.
     */
    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT, offset: Int = 0): RecipeSearchOutcome

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}
