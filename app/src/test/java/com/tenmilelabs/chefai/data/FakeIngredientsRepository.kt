package com.tenmilelabs.chefai.data

import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.domain.model.Ingredient
import com.tenmilelabs.chefai.domain.repository.IngredientsRepository
import com.tenmilelabs.chefai.testData.testIngredients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class FakeIngredientsRepository @Inject constructor() : IngredientsRepository {

    override fun getAll(): Flow<List<Ingredient>> {
        return flowOf(testIngredients.toDomain())
    }
}
