package com.tenmilelabs.chefai.household.data.mapper

import com.tenmilelabs.chefai.core.data.local.room.HouseholdEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdInviteEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdMemberEntity
import com.tenmilelabs.chefai.core.domain.model.HouseholdRole
import com.tenmilelabs.chefai.household.data.network.CreateInviteResult
import com.tenmilelabs.chefai.household.data.network.dto.HouseholdResponse
import com.tenmilelabs.chefai.household.data.network.dto.InvitePreviewResponse
import com.tenmilelabs.chefai.household.data.network.dto.InviteSummaryResponse
import com.tenmilelabs.chefai.household.data.network.dto.MemberResponse
import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvite
import com.tenmilelabs.chefai.household.domain.model.HouseholdInviteLink
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvitePreview
import com.tenmilelabs.chefai.household.domain.model.HouseholdMember
import com.tenmilelabs.chefai.household.domain.model.PendingHouseholdInvite
import java.util.UUID

// ── Network → domain ───────────────────────────────────────────────────────────────────────

fun HouseholdResponse.toDomain(): Household = Household(
    uuid = UUID.fromString(id),
    name = name,
    ownerId = UUID.fromString(ownerId),
    members = members.map { it.toDomain() },
)

fun MemberResponse.toDomain(): HouseholdMember = HouseholdMember(
    userId = UUID.fromString(userId),
    displayName = displayName,
    avatarUrl = avatarUrl,
    role = HouseholdRole.valueOf(role),
)

fun InviteSummaryResponse.toDomain(): HouseholdInvite = HouseholdInvite(
    inviteId = UUID.fromString(id),
    inviteeEmail = inviteeEmail,
    singleUse = singleUse,
    maxUses = maxUses,
    useCount = useCount,
    expiresAt = expiresAt,
    createdAt = createdAt,
)

/** The backend has no separate short code — the raw token itself is what a manual-entry field
 *  accepts (paste or type), so it doubles as [HouseholdInviteLink.manualCode]. */
fun CreateInviteResult.Success.toDomain(): HouseholdInviteLink =
    HouseholdInviteLink(url = url, manualCode = token, expiresAt = expiresAt)

fun InvitePreviewResponse.toDomain(): HouseholdInvitePreview = HouseholdInvitePreview(
    householdName = householdName,
    inviterDisplayName = inviterDisplayName,
)

/** No names available from this shape — see [PendingHouseholdInvite]'s doc. */
fun InviteSummaryResponse.toPendingInvite(): PendingHouseholdInvite = PendingHouseholdInvite(
    inviteId = UUID.fromString(id),
    householdId = UUID.fromString(householdId),
    expiresAt = expiresAt,
    createdAt = createdAt,
)

// ── Network → local cache ──────────────────────────────────────────────────────────────────

fun HouseholdResponse.toEntity(updatedAt: Long): HouseholdEntity = HouseholdEntity(
    uuid = UUID.fromString(id),
    name = name,
    ownerId = UUID.fromString(ownerId),
    createdAt = updatedAt,
    updatedAt = updatedAt,
)

fun MemberResponse.toEntity(householdId: UUID): HouseholdMemberEntity = HouseholdMemberEntity(
    householdId = householdId,
    userId = UUID.fromString(userId),
    displayName = displayName,
    avatarUrl = avatarUrl,
    role = role,
    joinedAt = joinedAt,
)

fun InviteSummaryResponse.toPendingInviteEntity(): HouseholdInviteEntity = HouseholdInviteEntity(
    inviteId = UUID.fromString(id),
    householdId = UUID.fromString(householdId),
    expiresAt = expiresAt,
    createdAt = createdAt,
)

fun HouseholdInviteEntity.toDomain(): PendingHouseholdInvite = PendingHouseholdInvite(
    inviteId = inviteId,
    householdId = householdId,
    expiresAt = expiresAt,
    createdAt = createdAt,
)

// ── Local cache → domain ───────────────────────────────────────────────────────────────────

fun HouseholdEntity.toDomain(members: List<HouseholdMemberEntity>): Household = Household(
    uuid = uuid,
    name = name,
    ownerId = ownerId,
    members = members.map { it.toDomain() },
)

fun HouseholdMemberEntity.toDomain(): HouseholdMember = HouseholdMember(
    userId = userId,
    displayName = displayName,
    avatarUrl = avatarUrl,
    role = HouseholdRole.valueOf(role),
)
