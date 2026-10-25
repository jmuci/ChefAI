package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.recipes.domain.repository.CookHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

class FakeCookHistoryRepository : CookHistoryRepository {

    private val cookedIds = MutableStateFlow<Set<UUID>>(emptySet())

    override fun observeCookedRecipeIds(): Flow<Set<UUID>> = cookedIds

    fun setCookedRecipeIds(ids: Set<UUID>) {
        cookedIds.value = ids
    }
}
