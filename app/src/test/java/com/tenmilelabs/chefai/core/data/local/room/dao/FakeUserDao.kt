package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.UserWithRecipes
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeUserDao : UserDao {

    private val users = MutableStateFlow<Map<UUID, UserEntity>>(emptyMap())

    override suspend fun getAllUsers(): List<UserEntity> = users.value.values.toList()

    override fun observeAll(): Flow<List<UserEntity>> = users.map { it.values.toList() }

    override suspend fun getUserById(uuid: UUID): UserEntity? = users.value[uuid]

    override fun observeUserById(uuid: UUID): Flow<UserEntity?> = users.map { it[uuid] }

    override suspend fun getUserWithRecipes(uuid: UUID): UserWithRecipes? = null

    override fun observeUsersWithRecipes(): Flow<List<UserWithRecipes>> =
        MutableStateFlow(emptyList())

    override suspend fun deleteUser(uuid: UUID) {
        users.value = users.value.toMutableMap().apply { remove(uuid) }
    }

    override suspend fun deleteAllUsers() {
        users.value = emptyMap()
    }

    override suspend fun upsertUser(user: UserEntity) {
        users.value = users.value.toMutableMap().apply { put(user.uuid, user) }
    }

    override suspend fun upsertAll(users: List<UserEntity>) {
        this.users.value = this.users.value.toMutableMap().apply {
            users.forEach { put(it.uuid, it) }
        }
    }

    override suspend fun getDirty(): List<UserEntity> =
        users.value.values.filter { it.syncState == SyncState.PENDING }
}
