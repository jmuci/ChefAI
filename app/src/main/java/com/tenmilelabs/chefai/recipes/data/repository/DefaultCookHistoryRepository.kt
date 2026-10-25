package com.tenmilelabs.chefai.recipes.data.repository

import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeCookEventDao
import com.tenmilelabs.chefai.recipes.domain.repository.CookHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCookHistoryRepository @Inject constructor(
    private val recipeCookEventDao: RecipeCookEventDao,
) : CookHistoryRepository {

    override fun observeCookedRecipeIds(): Flow<Set<UUID>> =
        recipeCookEventDao.observeCookedRecipeIds().map { it.toSet() }
}
