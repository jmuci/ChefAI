package com.tenmilelabs.chefai.data.source.local

import com.tenmilelabs.chefai.data.source.local.room.TagEntity
import com.tenmilelabs.chefai.data.source.local.room.dao.TagDao
import com.tenmilelabs.chefai.data.source.local.room.relations.TagWithRecipes
import com.tenmilelabs.chefai.data.source.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeTagDao : TagDao {

    private val tagsFlow = MutableStateFlow<List<TagEntity>>(emptyList())

    override suspend fun getAllTags(): List<TagEntity> {
        return tagsFlow.value
    }

    override fun observeAll(): Flow<List<TagEntity>> {
        return tagsFlow
    }

    override suspend fun getTagById(uuid: UUID): TagEntity? {
        return tagsFlow.value.find { it.uuid == uuid }
    }

    override fun observeTagById(uuid: UUID): Flow<TagEntity?> {
        return tagsFlow.map { tags -> tags.find { it.uuid == uuid } }
    }

    override suspend fun getTagWithRecipes(uuid: UUID): TagWithRecipes? {
        // Not implemented for fake
        return null
    }

    override fun observeTagsWithRecipes(): Flow<List<TagWithRecipes>> {
        // Not implemented for fake
        return MutableStateFlow(emptyList())
    }

    override suspend fun deleteTag(uuid: UUID) {
        tagsFlow.value = tagsFlow.value.filterNot { it.uuid == uuid }
    }

    override suspend fun deleteAllTags() {
        tagsFlow.value = emptyList()
    }

    override suspend fun upsertTag(tag: TagEntity) {
        val currentList = tagsFlow.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.uuid == tag.uuid }
        if (existingIndex != -1) {
            currentList[existingIndex] = tag
        } else {
            currentList.add(tag)
        }
        tagsFlow.value = currentList
    }

    override suspend fun upsertAll(tags: List<TagEntity>) {
        val currentList = tagsFlow.value.toMutableList()
        tags.forEach { tag ->
            val existingIndex = currentList.indexOfFirst { it.uuid == tag.uuid }
            if (existingIndex != -1) {
                currentList[existingIndex] = tag
            } else {
                currentList.add(tag)
            }
        }
        tagsFlow.value = currentList
    }

    override suspend fun getDirty(): List<TagEntity> {
        return tagsFlow.value.filter { it.syncState == SyncState.PENDING }
    }
}
