package com.tenmilelabs.chefai.household.domain.model

import java.util.UUID

/**
 * The signed-in user's household — at most one per user (see ADR-014), so there is deliberately no
 * concept of "other households" or a switcher anywhere in this app's domain model.
 */
data class Household(
    val uuid: UUID,
    val name: String,
    val ownerId: UUID,
    val members: List<HouseholdMember>,
)
