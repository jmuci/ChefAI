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
     * [RecipeSearchSource.LOCAL_FALLBACK] — whenever the network call fails; that fallback is a
     * deliberate degradation, not a second source of truth, so it never carries `hasMore = true`.
     *
     * Anonymous sessions are served over the network too, scoped to the public catalog — they are
     * *not* a fallback case. See ChefAI#184.
     */
    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT, offset: Int = 0): RecipeSearchOutcome

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}
