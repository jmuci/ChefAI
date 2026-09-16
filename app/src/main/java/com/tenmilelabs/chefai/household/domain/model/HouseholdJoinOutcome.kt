package com.tenmilelabs.chefai.household.domain.model

/**
 * The result of attempting to join a household via an invite (link token or in-app accept). A
 * network/server error is surfaced separately as `Result.failure` by the repository — this type
 * only distinguishes the outcomes the UI must render differently on success.
 */
sealed interface HouseholdJoinOutcome {
    data class Joined(val household: Household) : HouseholdJoinOutcome
    data object InvalidOrExpired : HouseholdJoinOutcome
    data object AlreadyInAHousehold : HouseholdJoinOutcome
}
