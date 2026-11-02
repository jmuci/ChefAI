package com.tenmilelabs.chefai.collections.domain.repository

import com.tenmilelabs.chefai.collections.domain.model.BookmarkUpdate
import com.tenmilelabs.chefai.collections.domain.model.RecipeBookmark
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface CollectionsRepository {
    fun observeBookmarkedRecipeIds(userId: UUID): Flow<Set<UUID>>

    /** The user's live bookmarks, keyed by recipe id. A recipe missing from the map isn't bookmarked. */
    fun observeBookmarks(userId: UUID): Flow<Map<UUID, RecipeBookmark>>

    /** Applies [update] to [recipeId]'s bookmark — see [BookmarkUpdate] for what each one does. */
    suspend fun update(userId: UUID, recipeId: UUID, update: BookmarkUpdate)

    suspend fun addBookmark(userId: UUID, recipeId: UUID) = update(userId, recipeId, BookmarkUpdate.Bookmark)
    suspend fun removeBookmark(userId: UUID, recipeId: UUID) = update(userId, recipeId, BookmarkUpdate.Remove)
}
