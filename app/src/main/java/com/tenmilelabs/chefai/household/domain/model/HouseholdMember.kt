package com.tenmilelabs.chefai.household.domain.model

import java.util.UUID

/**
 * One member of a [Household]. `displayName`/`avatarUrl` are denormalized off the server's own
 * user record at refresh time — see [com.tenmilelabs.chefai.core.data.local.room.HouseholdMemberEntity].
 */
data class HouseholdMember(
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String,
    val role: HouseholdRole,
)
