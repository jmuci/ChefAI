package com.tenmilelabs.chefai.data

import com.tenmilelabs.chefai.data.mapper.toDomain
import com.tenmilelabs.chefai.domain.model.Label
import com.tenmilelabs.chefai.domain.repository.LabelsRepository
import com.tenmilelabs.chefai.testData.testLabels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class FakeLabelsRepository @Inject constructor() : LabelsRepository {

    override fun getAll(): Flow<List<Label>> {
        return flowOf(testLabels.toDomain())
    }
}
