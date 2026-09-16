package com.tenmilelabs.chefai.household.domain.model

import java.util.UUID

/**
 * An owner's-eye view of one of their own household's outstanding invites — distinct from
 * [PendingHouseholdInvite], which is the invitee's-eye view of an invite addressed to *them*.
 * Never carries a raw token; the server never returns one in a listing.
 */
data class HouseholdInvite(
    val inviteId: UUID,
    val inviteeEmail: String?,
    val singleUse: Boolean,
    val maxUses: Int?,
    val useCount: Int,
    val expiresAt: Long,
    val createdAt: Long,
)
