package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.HouseholdEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdInviteEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdMemberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeHouseholdDao : HouseholdDao {

    private val households = mutableMapOf<UUID, HouseholdEntity>()
    private val members = mutableMapOf<UUID, MutableList<HouseholdMemberEntity>>()
    private val invites = mutableMapOf<UUID, HouseholdInviteEntity>()

    private val householdsFlow = MutableStateFlow<Map<UUID, HouseholdEntity>>(emptyMap())
    private val membersFlow = MutableStateFlow<Map<UUID, List<HouseholdMemberEntity>>>(emptyMap())
    private val invitesFlow = MutableStateFlow<Map<UUID, HouseholdInviteEntity>>(emptyMap())

    private fun notifyHouseholds() { householdsFlow.value = households.toMap() }
    private fun notifyMembers() { membersFlow.value = members.mapValues { it.value.toList() } }
    private fun notifyInvites() { invitesFlow.value = invites.toMap() }

    override fun observeHousehold(id: UUID): Flow<HouseholdEntity?> = householdsFlow.map { it[id] }

    override fun observeCachedHousehold(): Flow<HouseholdEntity?> = householdsFlow.map { it.values.firstOrNull() }

    override suspend fun getCachedHouseholdId(): UUID? = households.keys.firstOrNull()

    override fun observeMembers(id: UUID): Flow<List<HouseholdMemberEntity>> =
        membersFlow.map { it[id].orEmpty().sortedWith(compareBy({ it.role }, { it.displayName })) }

    override fun observePendingInvites(): Flow<List<HouseholdInviteEntity>> =
        invitesFlow.map { it.values.sortedByDescending { invite -> invite.createdAt } }

    override suspend fun getCachedInviteIds(): List<UUID> = invites.keys.toList()

    override suspend fun upsertHousehold(household: HouseholdEntity) {
        households[household.uuid] = household
        notifyHouseholds()
    }

    override suspend fun upsertMembers(members: List<HouseholdMemberEntity>) {
        members.forEach { member ->
            this.members.getOrPut(member.householdId) { mutableListOf() }
                .removeAll { it.householdId == member.householdId && it.userId == member.userId }
            this.members.getOrPut(member.householdId) { mutableListOf() }.add(member)
        }
        notifyMembers()
    }

    override suspend fun upsertInvites(invites: List<HouseholdInviteEntity>) {
        invites.forEach { this.invites[it.inviteId] = it }
        notifyInvites()
    }

    override suspend fun clearMembers(id: UUID) {
        members.remove(id)
        notifyMembers()
    }

    override suspend fun removeInvite(inviteId: UUID) {
        invites.remove(inviteId)
        notifyInvites()
    }

    override suspend fun clearInvitesForHousehold(id: UUID) {
        invites.values.filter { it.householdId == id }.forEach { invites.remove(it.inviteId) }
        notifyInvites()
    }

    override suspend fun deleteHouseholdRow(id: UUID) {
        households.remove(id)
        notifyHouseholds()
    }

    override suspend fun deleteHousehold(id: UUID) {
        deleteHouseholdRow(id)
        clearMembers(id)
        clearInvitesForHousehold(id)
    }
}
