package com.tenmilelabs.chefai.household.domain.model

/**
 * The result of attempting to join a household via an invite (link token or in-app accept).
 *
 * Folds transient failures in as [NetworkError] rather than leaving them to a `Result` wrapper
 * around this type — matching [com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
 * .getOrFetchRecipe]'s `RecipeFetchResult` precedent, the established shape in this codebase for
 * "business outcome vs. transient failure." `HouseholdRepository.joinWithToken`/`acceptInvite`
 * return this type directly, unwrapped, so a caller writes one exhaustive `when` instead of both
 * unwrapping a `Result` and switching on a nested sealed type.
 */
sealed interface HouseholdJoinOutcome {
    data class Joined(val household: Household) : HouseholdJoinOutcome
    data object InvalidOrExpired : HouseholdJoinOutcome
    data object AlreadyInAHousehold : HouseholdJoinOutcome

    /** The invite might be valid, but it couldn't be resolved — a transient failure worth retrying. */
    data object NetworkError : HouseholdJoinOutcome
}
