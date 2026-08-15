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
        // Anonymous sessions have no JWT and would 401 on every keystroke — skip the guaranteed
        // round trip and go straight to the local fallback.
        if (sessionManager.userSession.value is UserSession.Anonymous) {
            return localFallback(query, limit)
        }

        return when (val result = apiService.search(query, limit, offset)) {
            is RecipeSearchNetworkResult.Success -> result.response.toOutcome()
            is RecipeSearchNetworkResult.Error -> localFallback(query, limit)
            RecipeSearchNetworkResult.Unauthorized -> onUnauthorized(query, limit, offset)
        }
    }

    /** Mirrors SyncWorker.doWork(): a single refresh-then-retry, never a loop. */
    private suspend fun onUnauthorized(query: String, limit: Int, offset: Int): RecipeSearchOutcome {
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
