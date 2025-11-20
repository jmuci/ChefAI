package com.tenmilelabs.chefai.domain.repository

import com.tenmilelabs.chefai.domain.model.Ingredient
import kotlinx.coroutines.flow.Flow

interface IngredientsRepository {
    fun getAll(): Flow<List<Ingredient>>
}
