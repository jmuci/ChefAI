package com.tenmilelabs.chefai.household.domain.model

/**
 * What a link opened before sign-in shows before committing to join — from `GET
 * /households/invites/preview?token=`. No [PendingHouseholdInvite.inviteId]: the backend's preview
 * response carries none, and the link path accepts by the token itself
 * ([com.tenmilelabs.chefai.household.domain.repository.HouseholdRepository.joinWithToken]), not by
 * an invite id.
 */
data class HouseholdInvitePreview(
    val householdName: String,
    val inviterDisplayName: String,
)
