package com.tenmilelabs.chefai.data.repository

import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.data.source.local.room.dao.IngredientDao
import com.tenmilelabs.chefai.domain.model.Ingredient
import com.tenmilelabs.chefai.domain.repository.IngredientsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class IngredientsRepositoryImpl @Inject constructor(
    private val ingredientDao: IngredientDao
) : IngredientsRepository {

    override fun getAll(): Flow<List<Ingredient>> {
        return ingredientDao.observeAll().map { it.toDomain() }
    }
}
