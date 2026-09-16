package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.Index
import java.util.UUID

/**
 * A member of [HouseholdEntity]. `displayName`/`avatarUrl` are denormalized off the server's own
 * join to its users table at refresh time — this cache has no local `users`-style relation to join
 * against for a household-mate the device may never otherwise have synced.
 *
 * Same read-through, wholesale-replace caching model as [HouseholdEntity] — see that entity's doc
 * for why this deliberately does not implement
 * [com.tenmilelabs.chefai.core.data.local.util.SyncableEntity].
 */
@Entity(
    tableName = "household_members",
    primaryKeys = ["householdId", "userId"],
    indices = [Index("householdId")],
)
data class HouseholdMemberEntity(
    val householdId: UUID,
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String,
    /** "OWNER" | "MEMBER" — see `com.tenmilelabs.chefai.household.domain.model.HouseholdRole`. */
    val role: String,
    val joinedAt: Long,
)
