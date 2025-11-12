package com.tenmilelabs.chefai.data.source.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tenmilelabs.chefai.data.source.local.room.TagEntity
import com.tenmilelabs.chefai.data.source.local.room.relations.TagWithRecipes
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the tags table.
 */
@Dao
interface TagDao {
    @Query("SELECT * FROM tags")
    suspend fun getAllTags(): List<TagEntity>

    @Query("SELECT * FROM tags")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE uuid = :uuid")
    suspend fun getTagById(uuid: UUID): TagEntity?

    @Query("SELECT * FROM tags WHERE uuid = :uuid")
    fun observeTagById(uuid: UUID): Flow<TagEntity?>

    @Transaction
    @Query("SELECT * FROM tags WHERE uuid = :uuid")
    suspend fun getTagWithRecipes(uuid: UUID): TagWithRecipes?

    @Transaction
    @Query("SELECT * FROM tags")
    fun observeTagsWithRecipes(): Flow<List<TagWithRecipes>>

    @Query("DELETE FROM tags WHERE uuid = :uuid")
    suspend fun deleteTag(uuid: UUID)

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Upsert
    suspend fun upsertTag(tag: TagEntity)

    @Upsert
    suspend fun upsertAll(tags: List<TagEntity>)

    @Query("SELECT * FROM tags WHERE syncState = 'PENDING'")
    suspend fun getDirty(): List<TagEntity>
}
