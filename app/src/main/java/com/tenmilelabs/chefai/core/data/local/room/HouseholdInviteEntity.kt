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
 * No `householdName`/`inviterDisplayName`, despite this doc originally assuming denormalized
 * names: the backend's `GET /households/invites/pending` (what populates this table) returns
 * `InviteSummaryResponse`, which carries neither field — only the link-preview endpoint
 * (`GET /households/invites/preview?token=`, keyed by a token a pending invite never has) returns
 * names, into a separate, unpersisted domain type
 * ([com.tenmilelabs.chefai.household.domain.model.HouseholdInvitePreview]). So an in-app pending
 * invite is always nameless; a UI falls back to something generic ("You've been invited to a
 * household") plus [expiresAt]. Wholesale-replaced on refresh, same as [HouseholdEntity]/
 * [HouseholdMemberEntity].
 */
@Entity(tableName = "household_invites")
data class HouseholdInviteEntity(
    @PrimaryKey val inviteId: UUID,
    val householdId: UUID,
    val expiresAt: Long,
    val createdAt: Long,
)
