package com.tenmilelabs.chefai.domain.repository

import com.tenmilelabs.chefai.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagsRepository {

    suspend fun getAll(): List<Tag>
    fun observeAll(): Flow<List<Tag>>
}
