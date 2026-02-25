package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.SourceClassificationEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeSourceClassificationDao : SourceClassificationDao {

    private val sourceClassificationsFlow =
        MutableStateFlow<List<SourceClassificationEntity>>(emptyList())

    override suspend fun getAllSourceClassifications(): List<SourceClassificationEntity> {
        return sourceClassificationsFlow.value
    }

    override fun observeAll(): Flow<List<SourceClassificationEntity>> {
        return sourceClassificationsFlow
    }

    override suspend fun getSourceClassificationById(uuid: UUID): SourceClassificationEntity? {
        return sourceClassificationsFlow.value.find { it.uuid == uuid }
    }

    override fun observeSourceClassificationById(uuid: UUID): Flow<SourceClassificationEntity?> {
        return sourceClassificationsFlow.map { sourceClassifications ->
            sourceClassifications.find { it.uuid == uuid }
        }
    }

    override suspend fun deleteSourceClassification(uuid: UUID) {
        sourceClassificationsFlow.value =
            sourceClassificationsFlow.value.filterNot { it.uuid == uuid }
    }

    override suspend fun deleteAllSourceClassifications() {
        sourceClassificationsFlow.value = emptyList()
    }

    override suspend fun upsertSourceClassification(
        sourceClassification: SourceClassificationEntity
    ) {
        val currentList = sourceClassificationsFlow.value.toMutableList()
        val existingIndex =
            currentList.indexOfFirst { it.uuid == sourceClassification.uuid }

        if (existingIndex != -1) {
            currentList[existingIndex] = sourceClassification
        } else {
            currentList.add(sourceClassification)
        }

        sourceClassificationsFlow.value = currentList
    }

    override suspend fun upsertAll(
        sourceClassifications: List<SourceClassificationEntity>
    ) {
        val currentList = sourceClassificationsFlow.value.toMutableList()

        sourceClassifications.forEach { sourceClassification ->
            val existingIndex =
                currentList.indexOfFirst { it.uuid == sourceClassification.uuid }

            if (existingIndex != -1) {
                currentList[existingIndex] = sourceClassification
            } else {
                currentList.add(sourceClassification)
            }
        }

        sourceClassificationsFlow.value = currentList
    }

    override suspend fun getDirty(): List<SourceClassificationEntity> {
        return sourceClassificationsFlow.value
            .filter { it.syncState == SyncState.PENDING }
    }
}