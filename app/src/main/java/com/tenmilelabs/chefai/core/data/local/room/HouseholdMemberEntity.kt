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
    // A plain Index("householdId") would be redundant with the composite primary key above —
    // SQLite already backs (householdId, userId) with an implicit index usable for an
    // equality lookup on the leftmost column alone. This composite index instead also covers
    // HouseholdDao.observeMembers's `ORDER BY role, displayName`, so that query is a pure index
    // scan rather than a filter-then-sort.
    indices = [Index(value = ["householdId", "role", "displayName"])],
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
