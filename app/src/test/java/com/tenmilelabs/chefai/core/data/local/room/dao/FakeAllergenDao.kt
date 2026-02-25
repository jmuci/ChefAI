package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.AllergenEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeAllergenDao : AllergenDao {

    private val allergensFlow = MutableStateFlow<List<AllergenEntity>>(emptyList())

    override suspend fun getAllAllergens(): List<AllergenEntity> {
        return allergensFlow.value
    }

    override fun observeAll(): Flow<List<AllergenEntity>> {
        return allergensFlow
    }

    override suspend fun getAllergenById(uuid: UUID): AllergenEntity? {
        return allergensFlow.value.find { it.uuid == uuid }
    }

    override fun observeAllergenById(uuid: UUID): Flow<AllergenEntity?> {
        return allergensFlow.map { allergens ->
            allergens.find { it.uuid == uuid }
        }
    }

    override suspend fun deleteAllergen(uuid: UUID) {
        allergensFlow.value = allergensFlow.value.filterNot { it.uuid == uuid }
    }

    override suspend fun deleteAllAllergens() {
        allergensFlow.value = emptyList()
    }

    override suspend fun upsertAllergen(allergen: AllergenEntity) {
        val currentList = allergensFlow.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.uuid == allergen.uuid }

        if (existingIndex != -1) {
            currentList[existingIndex] = allergen
        } else {
            currentList.add(allergen)
        }

        allergensFlow.value = currentList
    }

    override suspend fun upsertAll(allergens: List<AllergenEntity>) {
        val currentList = allergensFlow.value.toMutableList()

        allergens.forEach { allergen ->
            val existingIndex = currentList.indexOfFirst { it.uuid == allergen.uuid }

            if (existingIndex != -1) {
                currentList[existingIndex] = allergen
            } else {
                currentList.add(allergen)
            }
        }

        allergensFlow.value = currentList
    }

    override suspend fun getDirty(): List<AllergenEntity> {
        return allergensFlow.value.filter { it.syncState == SyncState.PENDING }
    }
}