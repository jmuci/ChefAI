package com.tenmilelabs.chefai.collections.data.repository

import com.tenmilelabs.chefai.collections.domain.model.BookmarkUpdate
import com.tenmilelabs.chefai.collections.domain.model.RecipeBookmark
import com.tenmilelabs.chefai.collections.domain.model.createsBookmark
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeCollectionsRepository : CollectionsRepository {

    private val _bookmarks = MutableStateFlow<Map<UUID, RecipeBookmark>>(emptyMap())

    /**
     * When set, the next update that can create a bookmark ([BookmarkUpdate.Bookmark] or
     * [BookmarkUpdate.Star]) throws this instead of succeeding, then resets to null.
     */
    var exceptionToThrowOnAddBookmark: Throwable? = null

    /** Every update applied, in order. */
    val updates = mutableListOf<Pair<UUID, BookmarkUpdate>>()

    override fun observeBookmarkedRecipeIds(userId: UUID): Flow<Set<UUID>> =
        _bookmarks.map { it.keys }

    override fun observeBookmarks(userId: UUID): Flow<Map<UUID, RecipeBookmark>> = _bookmarks

    override suspend fun update(userId: UUID, recipeId: UUID, update: BookmarkUpdate) {
        if (update.createsBookmark) {
            exceptionToThrowOnAddBookmark?.let {
                exceptionToThrowOnAddBookmark = null
                throw it
            }
        }
        updates += recipeId to update
        val current = _bookmarks.value
        val existing = current[recipeId]
        _bookmarks.value = when (update) {
            BookmarkUpdate.Bookmark -> if (existing != null) current else current + (recipeId to RecipeBookmark(recipeId))
            BookmarkUpdate.Star -> current + (recipeId to (existing ?: RecipeBookmark(recipeId)).copy(isStarred = true))
            BookmarkUpdate.Unstar -> existing?.let { current + (recipeId to it.copy(isStarred = false)) } ?: current
            BookmarkUpdate.Remove -> current - recipeId
            is BookmarkUpdate.SetGoTo -> existing?.let { current + (recipeId to it.copy(isGoTo = update.goTo)) } ?: current
        }
    }

    fun setBookmarkedIds(ids: Set<UUID>) {
        _bookmarks.value = ids.associateWith { RecipeBookmark(it) }
    }

    fun setBookmarks(bookmarks: List<RecipeBookmark>) {
        _bookmarks.value = bookmarks.associateBy { it.recipeId }
    }
}
