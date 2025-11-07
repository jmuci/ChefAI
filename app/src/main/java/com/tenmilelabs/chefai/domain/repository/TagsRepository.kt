package com.tenmilelabs.chefai.domain.repository

import com.tenmilelabs.chefai.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagsRepository {
    fun getAll(): Flow<List<Tag>>
}
