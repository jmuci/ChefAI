package com.tenmilelabs.chefai.data.repository

import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.data.source.local.room.dao.TagDao
import com.tenmilelabs.chefai.domain.model.Tag
import com.tenmilelabs.chefai.domain.repository.TagsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TagsRepositoryImpl @Inject constructor(
    private val tagDao: TagDao
) : TagsRepository {

    override fun getAll(): Flow<List<Tag>> {
        return tagDao.getAll().map { it.toDomain() }
    }
}
