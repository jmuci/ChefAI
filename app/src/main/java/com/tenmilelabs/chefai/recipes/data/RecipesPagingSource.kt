package com.tenmilelabs.chefai.recipes.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipePreviewDomain
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.UUID

/**
 * PagingSource for loading recipes from the local database.
 *
 * @param recipeDao The DAO for accessing recipe data
 * @param userId The ID of the user whose recipes to load
 */
class RecipesPagingSource(
    private val recipeDao: RecipeDao,
    private val userId: UUID
) : PagingSource<Int, RecipePreview>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RecipePreview> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            val offset = page * pageSize

            Timber.d("Loading recipes page=$page, pageSize=$pageSize, offset=$offset")

            // Load recipes from database with pagination
            val recipeEntities = recipeDao.getRecipesWithDetailsForUserPaginated(
                userId = userId,
                limit = pageSize,
                offset = offset
            )
            // Map to domain models
            val recipes = recipeEntities.toRecipePreviewDomain()

            Timber.d("Loaded ${recipes.size} recipes for page $page")

            // Calculate next/prev keys
            val nextKey = if (recipes.size < pageSize) {
                // No more data
                null
            } else {
                page + 1
            }

            val prevKey = if (page == 0) null else page - 1

            LoadResult.Page(
                data = recipes,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            Timber.e(e, "Error loading recipes page")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, RecipePreview>): Int? {
        // Try to find the page key of the closest page to the most recently accessed index.
        // This will be the key for the page that contains the item closest to the
        // currently displayed items.
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}