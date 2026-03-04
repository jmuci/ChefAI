package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.LabelEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.LabelWithRecipes
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the labels table.
 */
@Dao
interface LabelDao {
    @Query("SELECT * FROM labels")
    suspend fun getAllLabels(): List<LabelEntity>

    @Query("SELECT * FROM labels")
    fun observeAll(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels WHERE uuid = :uuid")
    suspend fun getLabelById(uuid: UUID): LabelEntity?

    @Query("SELECT * FROM labels WHERE uuid = :uuid")
    fun observeLabelById(uuid: UUID): Flow<LabelEntity?>

    @Transaction
    @Query("SELECT * FROM labels WHERE uuid = :uuid")
    suspend fun getLabelWithRecipes(uuid: UUID): LabelWithRecipes?

    @Transaction
    @Query("SELECT * FROM labels")
    fun observeLabelsWithRecipes(): Flow<List<LabelWithRecipes>>

    @Query("DELETE FROM labels WHERE uuid = :uuid")
    suspend fun deleteLabel(uuid: UUID)

    @Query("DELETE FROM labels")
    suspend fun deleteAllLabels()

    @Upsert
    suspend fun upsertLabel(label: LabelEntity)

    @Upsert
    suspend fun upsertAll(labels: List<LabelEntity>)

    @Query("SELECT uuid FROM labels WHERE uuid IN (:uuids)")
    suspend fun getExistingIds(uuids: List<UUID>): List<UUID>
}
