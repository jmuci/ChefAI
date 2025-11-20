package com.tenmilelabs.chefai.data

import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.domain.model.Ingredient
import com.tenmilelabs.chefai.domain.model.Label
import com.tenmilelabs.chefai.domain.model.Tag
import com.tenmilelabs.chefai.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.testData.testIngredients
import com.tenmilelabs.chefai.testData.testLabels
import com.tenmilelabs.chefai.testData.testTags
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeMetadataRepository @Inject constructor(): MetadataRepository {
    override fun observeAllIngredients(): Flow<List<Ingredient>> {
        return flowOf(testIngredients.toDomain())
    }

    override fun observeAllTags(): Flow<List<Tag>> {
        return flowOf(testTags.toDomain())
    }

    override fun observeAllLabels(): Flow<List<Label>> {
        return flowOf(testLabels.toDomain())
    }
}