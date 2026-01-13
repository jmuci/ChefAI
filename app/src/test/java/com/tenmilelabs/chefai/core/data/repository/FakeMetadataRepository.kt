package com.tenmilelabs.chefai.core.data.repository

import com.tenmilelabs.chefai.core.domain.model.Ingredient
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.domain.repository.MetadataRepository
import com.tenmilelabs.chefai.core.testutil.testIngredients
import com.tenmilelabs.chefai.core.testutil.testLabels
import com.tenmilelabs.chefai.core.testutil.testTags
import com.tenmilelabs.chefai.recipes.data.mapper.toDomain
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