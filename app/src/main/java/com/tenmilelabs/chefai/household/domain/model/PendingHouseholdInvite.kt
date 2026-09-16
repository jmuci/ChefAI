package com.tenmilelabs.chefai.household.domain.model

import java.util.UUID

/**
 * An invite addressed to the signed-in user by email, from the in-app "you've been invited"
 * inbox (`GET /households/invites/pending`). Accepted/declined by [inviteId] — never a raw token,
 * which this listing never carries.
 *
 * No household or inviter name: the backend's summary listing doesn't return them (only the
 * separate, token-keyed link-preview endpoint does — see [HouseholdInvitePreview]). A UI showing
 * this card has no richer detail than "you've been invited to a household" plus [expiresAt].
 */
data class PendingHouseholdInvite(
    val inviteId: UUID,
    val householdId: UUID,
    val expiresAt: Long,
    val createdAt: Long,
)
