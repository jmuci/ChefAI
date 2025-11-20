package com.tenmilelabs.chefai.data.source.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.data.source.local.room.SourceClassificationEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the source_classifications table.
 */
@Dao
interface SourceClassificationDao {
    @Query("SELECT * FROM source_classifications")
    suspend fun getAllSourceClassifications(): List<SourceClassificationEntity>

    @Query("SELECT * FROM source_classifications")
    fun observeAll(): Flow<List<SourceClassificationEntity>>

    @Query("SELECT * FROM source_classifications WHERE uuid = :uuid")
    suspend fun getSourceClassificationById(uuid: UUID): SourceClassificationEntity?

    @Query("SELECT * FROM source_classifications WHERE uuid = :uuid")
    fun observeSourceClassificationById(uuid: UUID): Flow<SourceClassificationEntity?>

    @Query("DELETE FROM source_classifications WHERE uuid = :uuid")
    suspend fun deleteSourceClassification(uuid: UUID)

    @Query("DELETE FROM source_classifications")
    suspend fun deleteAllSourceClassifications()

    @Upsert
    suspend fun upsertSourceClassification(sourceClassification: SourceClassificationEntity)

    @Upsert
    suspend fun upsertAll(sourceClassifications: List<SourceClassificationEntity>)

    @Query("SELECT * FROM source_classifications WHERE syncState = 'PENDING'")
    suspend fun getDirty(): List<SourceClassificationEntity>
}
