package com.tenmilelabs.chefai.household.domain.model

/**
 * The result of creating a household invite — a shareable [url] for the Android share sheet, plus
 * a [manualCode] fallback for a link that can't be tapped (typed in by hand on another device, or
 * dictated over a call). [expiresAt] is epoch millis, or null if the invite never expires.
 */
data class HouseholdInviteLink(
    val url: String,
    val manualCode: String,
    val expiresAt: Long?,
)
