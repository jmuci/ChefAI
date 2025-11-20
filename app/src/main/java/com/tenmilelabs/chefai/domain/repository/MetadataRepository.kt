package com.tenmilelabs.chefai.domain.repository

import com.tenmilelabs.chefai.domain.model.Ingredient
import com.tenmilelabs.chefai.domain.model.Label
import com.tenmilelabs.chefai.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface MetadataRepository {

    fun observeAllIngredients(): Flow<List<Ingredient>>

    fun observeAllTags(): Flow<List<Tag>>

    fun observeAllLabels(): Flow<List<Label>>
}
