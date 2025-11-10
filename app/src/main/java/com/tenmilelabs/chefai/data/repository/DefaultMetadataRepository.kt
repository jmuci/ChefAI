package com.tenmilelabs.chefai.data.repository

import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.data.source.local.room.dao.IngredientDao
import com.tenmilelabs.chefai.data.source.local.room.dao.LabelDao
import com.tenmilelabs.chefai.data.source.local.room.dao.TagDao
import com.tenmilelabs.chefai.domain.model.Ingredient
import com.tenmilelabs.chefai.domain.model.Label
import com.tenmilelabs.chefai.domain.model.Tag
import com.tenmilelabs.chefai.domain.repository.MetadataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultMetadataRepository @Inject constructor(
    private val ingredientDao: IngredientDao,
    private val tagDao: TagDao,
    private val labelDao: LabelDao
) : MetadataRepository {

    override fun observeAllIngredients(): Flow<List<Ingredient>> {
        return ingredientDao.observeAll().map { it.toDomain() }
    }

    override fun observeAllTags(): Flow<List<Tag>> {
        return tagDao.observeAll().map { it.toDomain() }
    }

    override fun observeAllLabels(): Flow<List<Label>> {
        return labelDao.observeAll().map { it.toDomain() }
    }
}
