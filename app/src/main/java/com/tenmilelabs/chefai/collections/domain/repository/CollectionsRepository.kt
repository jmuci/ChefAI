package com.tenmilelabs.chefai.collections.domain.repository

import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface CollectionsRepository {
    fun observeBookmarkedRecipeIds(userId: UUID): Flow<Set<UUID>>
    suspend fun addBookmark(userId: UUID, recipeId: UUID)
    suspend fun removeBookmark(userId: UUID, recipeId: UUID)
}
