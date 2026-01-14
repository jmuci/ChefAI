package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.LabelEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.LabelWithRecipes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeLabelDao : LabelDao {

    private val labelsFlow = MutableStateFlow<List<LabelEntity>>(emptyList())

    override suspend fun getAllLabels(): List<LabelEntity> {
        return labelsFlow.value
    }

    override fun observeAll(): Flow<List<LabelEntity>> {
        return labelsFlow
    }

    override suspend fun getLabelById(uuid: UUID): LabelEntity? {
        return labelsFlow.value.find { it.uuid == uuid }
    }

    override fun observeLabelById(uuid: UUID): Flow<LabelEntity?> {
        return labelsFlow.map { labels -> labels.find { it.uuid == uuid } }
    }

    override suspend fun getLabelWithRecipes(uuid: UUID): LabelWithRecipes? {
        // Not implemented for fake
        return null
    }

    override fun observeLabelsWithRecipes(): Flow<List<LabelWithRecipes>> {
        // Not implemented for fake
        return MutableStateFlow(emptyList())
    }

    override suspend fun deleteLabel(uuid: UUID) {
        labelsFlow.value = labelsFlow.value.filterNot { it.uuid == uuid }
    }

    override suspend fun deleteAllLabels() {
        labelsFlow.value = emptyList()
    }

    override suspend fun upsertLabel(label: LabelEntity) {
        val currentList = labelsFlow.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.uuid == label.uuid }
        if (existingIndex != -1) {
            currentList[existingIndex] = label
        } else {
            currentList.add(label)
        }
        labelsFlow.value = currentList
    }
}
