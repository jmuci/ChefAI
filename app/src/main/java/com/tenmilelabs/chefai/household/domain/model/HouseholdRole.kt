package com.tenmilelabs.chefai.household.domain.model

/**
 * A user's role within a household — see ADR-014. Only `OWNER` may invite, remove members, rename
 * or delete the household; both roles may edit the household's shared meal plan and grocery list.
 */
enum class HouseholdRole { OWNER, MEMBER }
