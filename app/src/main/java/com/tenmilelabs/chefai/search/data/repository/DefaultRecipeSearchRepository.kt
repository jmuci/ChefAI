package com.tenmilelabs.chefai.search.data.repository

import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipePreviewDomain
import com.tenmilelabs.chefai.search.data.mapper.toRecipePreview
import com.tenmilelabs.chefai.search.data.network.RecipeSearchNetworkDataSource
import com.tenmilelabs.chefai.search.data.network.RecipeSearchNetworkResult
import com.tenmilelabs.chefai.search.data.network.dto.RecipeSearchResponseDto
import com.tenmilelabs.chefai.search.domain.repository.RecipeSearchOutcome
import com.tenmilelabs.chefai.search.domain.repository.RecipeSearchRepository
import com.tenmilelabs.chefai.search.domain.repository.RecipeSearchSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRecipeSearchRepository @Inject constructor(
    private val apiService: RecipeSearchNetworkDataSource,
    private val recipeDao: RecipeDao,
    private val sessionManager: SessionManager,
) : RecipeSearchRepository {

    override suspend fun search(query: String, limit: Int, offset: Int): RecipeSearchOutcome {
        // Anonymous sessions go over the wire like any other: the backend serves them the PUBLIC
        // catalog without a JWT. This used to short-circuit to localFallback() instead, which
        // capped anonymous search at whatever this device had already synced — ChefAI#184.
        return when (val result = apiService.search(query, limit, offset)) {
            is RecipeSearchNetworkResult.Success -> result.response.toOutcome()
            is RecipeSearchNetworkResult.Error -> localFallback(query, limit)
            RecipeSearchNetworkResult.Unauthorized -> onUnauthorized(query, limit, offset)
        }
    }

    /**
     * Mirrors SyncWorker.doWork(): a single refresh-then-retry, never a loop.
     *
     * An anonymous session has no refresh token, so refreshing would be a guaranteed-failing round
     * trip on a path that fires every keystroke — fall back straight away instead. A current
     * backend never 401s an anonymous search at all, so this branch only guards against pointing
     * at a server that predates ChefAI#184.
     */
    private suspend fun onUnauthorized(query: String, limit: Int, offset: Int): RecipeSearchOutcome {
        if (sessionManager.userSession.value is UserSession.Anonymous) {
            return localFallback(query, limit)
        }
        if (sessionManager.refreshToken().isFailure) {
            return localFallback(query, limit)
        }
        return when (val retryResult = apiService.search(query, limit, offset)) {
            is RecipeSearchNetworkResult.Success -> retryResult.response.toOutcome()
            else -> localFallback(query, limit)
        }
    }

    private fun RecipeSearchResponseDto.toOutcome() =
        RecipeSearchOutcome(
            results = results.map { it.toRecipePreview() },
            hasMore = hasMore,
            source = RecipeSearchSource.REMOTE,
        )

    private suspend fun localFallback(query: String, limit: Int): RecipeSearchOutcome {
        val rows = recipeDao.searchRecipesWithDetails(query, limit)
        return RecipeSearchOutcome(
            results = rows.toRecipePreviewDomain(),
            hasMore = false,
            source = RecipeSearchSource.LOCAL_FALLBACK,
        )
    }
}
