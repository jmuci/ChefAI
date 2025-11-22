package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.AllergenEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the allergens table.
 */
@Dao
interface AllergenDao {
    @Query("SELECT * FROM allergens")
    suspend fun getAllAllergens(): List<AllergenEntity>

    @Query("SELECT * FROM allergens")
    fun observeAll(): Flow<List<AllergenEntity>>

    @Query("SELECT * FROM allergens WHERE uuid = :uuid")
    suspend fun getAllergenById(uuid: UUID): AllergenEntity?

    @Query("SELECT * FROM allergens WHERE uuid = :uuid")
    fun observeAllergenById(uuid: UUID): Flow<AllergenEntity?>

    @Query("DELETE FROM allergens WHERE uuid = :uuid")
    suspend fun deleteAllergen(uuid: UUID)

    @Query("DELETE FROM allergens")
    suspend fun deleteAllAllergens()

    @Upsert
    suspend fun upsertAllergen(allergen: AllergenEntity)

    @Upsert
    suspend fun upsertAll(allergens: List<AllergenEntity>)

    @Query("SELECT * FROM allergens WHERE syncState = 'PENDING'")
    suspend fun getDirty(): List<AllergenEntity>
}
