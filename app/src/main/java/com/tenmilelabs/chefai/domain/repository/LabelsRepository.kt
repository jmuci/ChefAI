package com.tenmilelabs.chefai.domain.repository

import com.tenmilelabs.chefai.domain.model.Label
import kotlinx.coroutines.flow.Flow

interface LabelsRepository {
    fun getAll(): Flow<List<Label>>
}
