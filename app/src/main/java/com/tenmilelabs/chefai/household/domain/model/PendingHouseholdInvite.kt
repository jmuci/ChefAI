package com.tenmilelabs.chefai.household.domain.model

import java.util.UUID

/**
 * An invite addressed to the signed-in user by email — the in-app "you've been invited" inbox
 * (households backend prompt, decision 3). Accepted/declined by [inviteId], never by a raw token —
 * the server never hands this listing a token to accept with.
 */
data class PendingHouseholdInvite(
    val inviteId: UUID,
    val householdName: String,
    val inviterDisplayName: String,
)
