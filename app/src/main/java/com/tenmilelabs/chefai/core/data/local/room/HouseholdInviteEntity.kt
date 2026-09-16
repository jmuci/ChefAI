package com.tenmilelabs.chefai.core.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One invite addressed to the signed-in user by email — the in-app "you've been invited" inbox
 * (see the households backend/Android prompts, decision 3). Deliberately carries **no `token`
 * column**: accepting an in-app invite goes by [inviteId], never a raw token — the backend never
 * hands the listing endpoint this cache is built from a raw token to accept with (see
 * `households-backend-prompt.md` §0.4).
 *
 * Denormalized (`householdName`, `inviterDisplayName`) so the accept screen needs no extra fetch,
 * and wholesale-replaced on refresh, same as [HouseholdEntity]/[HouseholdMemberEntity].
 */
@Entity(tableName = "household_invites")
data class HouseholdInviteEntity(
    @PrimaryKey val inviteId: UUID,
    val householdId: UUID,
    val householdName: String,
    val inviterDisplayName: String,
    val createdAt: Long,
)
