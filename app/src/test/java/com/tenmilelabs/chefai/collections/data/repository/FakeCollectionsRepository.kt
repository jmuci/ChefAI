package com.tenmilelabs.chefai.collections.data.repository

import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeCollectionsRepository : CollectionsRepository {

    private val _bookmarkedIds = MutableStateFlow<Set<UUID>>(emptySet())

    override fun observeBookmarkedRecipeIds(userId: UUID): Flow<Set<UUID>> =
        _bookmarkedIds

    override suspend fun addBookmark(userId: UUID, recipeId: UUID) {
        _bookmarkedIds.value = _bookmarkedIds.value + recipeId
    }

    override suspend fun removeBookmark(userId: UUID, recipeId: UUID) {
        _bookmarkedIds.value = _bookmarkedIds.value - recipeId
    }

    fun setBookmarkedIds(ids: Set<UUID>) {
        _bookmarkedIds.value = ids
    }
}
