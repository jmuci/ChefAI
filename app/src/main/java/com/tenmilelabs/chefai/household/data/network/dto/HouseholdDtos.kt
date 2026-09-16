package com.tenmilelabs.chefai.household.data.network.dto

import kotlinx.serialization.Serializable

/** Wire shapes matching ktor-chefai's HouseholdDtos.kt exactly — see HouseholdRoutes.kt. */

@Serializable
data class CreateHouseholdRequest(val name: String)

@Serializable
data class RenameHouseholdRequest(val name: String)

@Serializable
data class MemberResponse(
    val userId: String,
    val displayName: String,
    val avatarUrl: String,
    val role: String,
    val joinedAt: Long,
)

@Serializable
data class HouseholdResponse(
    val id: String,
    val name: String,
    val ownerId: String,
    val members: List<MemberResponse>,
)

@Serializable
data class CreateInviteRequest(
    val inviteeEmail: String? = null,
    val singleUse: Boolean = true,
    val maxUses: Int? = null,
    val expiresInHours: Long? = null,
)

@Serializable
data class CreateInviteResponse(
    val token: String,
    val url: String,
    val expiresAt: Long,
    val singleUse: Boolean,
    val maxUses: Int?,
)

@Serializable
data class InviteSummaryResponse(
    val id: String,
    val householdId: String,
    val inviteeEmail: String?,
    val singleUse: Boolean,
    val maxUses: Int?,
    val useCount: Int,
    val expiresAt: Long,
    val createdAt: Long,
)

@Serializable
data class InvitePreviewResponse(
    val householdName: String,
    val inviterDisplayName: String,
)

@Serializable
data class JoinHouseholdRequest(val token: String)
