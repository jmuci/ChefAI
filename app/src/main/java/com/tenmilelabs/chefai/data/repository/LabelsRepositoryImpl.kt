package com.tenmilelabs.chefai.data.repository

import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.data.source.local.room.dao.LabelDao
import com.tenmilelabs.chefai.domain.model.Label
import com.tenmilelabs.chefai.domain.repository.LabelsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LabelsRepositoryImpl @Inject constructor(
    private val labelDao: LabelDao
) : LabelsRepository {

    override fun getAll(): Flow<List<Label>> {
        return labelDao.getAll().map { it.toDomain() }
    }
}
