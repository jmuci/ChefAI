package com.tenmilelabs.chefai.core.domain.repository

import com.tenmilelabs.chefai.core.domain.model.Ingredient
import com.tenmilelabs.chefai.core.domain.model.Label
import com.tenmilelabs.chefai.core.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface MetadataRepository {

    fun observeAllIngredients(): Flow<List<Ingredient>>

    fun observeAllTags(): Flow<List<Tag>>

    fun observeAllLabels(): Flow<List<Label>>
}
