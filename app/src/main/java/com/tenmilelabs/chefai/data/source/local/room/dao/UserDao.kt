package com.tenmilelabs.chefai.data.source.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tenmilelabs.chefai.data.source.local.room.UserEntity
import com.tenmilelabs.chefai.data.source.local.room.relations.UserWithRecipes
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the users table.
 */
@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT * FROM users")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE uuid = :uuid")
    suspend fun getUserById(uuid: UUID): UserEntity?

    @Query("SELECT * FROM users WHERE uuid = :uuid")
    fun observeUserById(uuid: UUID): Flow<UserEntity?>

    @Transaction
    @Query("SELECT * FROM users WHERE uuid = :uuid")
    suspend fun getUserWithRecipes(uuid: UUID): UserWithRecipes?

    @Transaction
    @Query("SELECT * FROM users")
    fun observeUsersWithRecipes(): Flow<List<UserWithRecipes>>

    @Query("DELETE FROM users WHERE uuid = :uuid")
    suspend fun deleteUser(uuid: UUID)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Upsert
    suspend fun upsertAll(users: List<UserEntity>)

    @Query("SELECT * FROM users WHERE syncState = 'PENDING'")
    suspend fun getDirty(): List<UserEntity>
}
