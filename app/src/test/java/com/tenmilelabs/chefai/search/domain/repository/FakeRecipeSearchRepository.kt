package com.tenmilelabs.chefai.search.domain.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Records every query it's called with (so tests can assert debounce/min-length behavior on call
 * count) and optionally [delayMillis] before resolving, so a test can start a second search while
 * the first is still in flight and assert the first one is genuinely cancelled by
 * `flatMapLatest`, not merely superseded.
 */
class FakeRecipeSearchRepository : RecipeSearchRepository {
    var outcomeToReturn = RecipeSearchOutcome(emptyList(), hasMore = false, source = RecipeSearchSource.REMOTE)
    var delayMillis: Long = 0

    private val _queriesReceived = mutableListOf<String>()
    val queriesReceived: List<String> get() = _queriesReceived

    var cancelledCount = 0
        private set

    override suspend fun search(query: String, limit: Int, offset: Int): RecipeSearchOutcome {
        _queriesReceived.add(query)
        try {
            if (delayMillis > 0) delay(delayMillis)
        } catch (e: CancellationException) {
            cancelledCount++
            throw e
        }
        return outcomeToReturn
    }
}
