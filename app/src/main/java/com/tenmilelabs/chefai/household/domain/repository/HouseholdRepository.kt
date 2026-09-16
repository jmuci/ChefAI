package com.tenmilelabs.chefai.household.domain.repository

import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.model.HouseholdInviteLink
import com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome
import com.tenmilelabs.chefai.household.domain.model.PendingHouseholdInvite
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Household membership, invites, and the read-through local cache backing them — see ADR-014.
 *
 * Unlike [com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository], this is **not**
 * offline-editable data with a dirty queue: every mutating method here is a direct network call
 * (implemented over plain REST, not the sync protocol), and [observeMyHousehold]/
 * [observePendingInvites] read a cache that [refresh] replaces wholesale rather than merges.
 */
interface HouseholdRepository {

    /** Cache-first: emits the local cache immediately, refreshed by [refresh]. Null = no household. */
    fun observeMyHousehold(userId: UUID): Flow<Household?>

    fun observePendingInvites(): Flow<List<PendingHouseholdInvite>>

    /** Re-fetches the caller's household and pending invites and replaces the local cache wholesale. */
    suspend fun refresh(): Result<Unit>

    suspend fun createHousehold(name: String): Result<Household>

    /** Self-service leave, available to a member of any role — including the owner. */
    suspend fun leaveHousehold(): Result<Unit>

    /** Owner-only removal of a different member. */
    suspend fun removeMember(userId: UUID): Result<Unit>

    /** Owner-only. Creates a fresh, shareable invite link. */
    suspend fun createInviteLink(): Result<HouseholdInviteLink>

    /** Owner-only. Creates a pending invite for an existing user's email, surfaced to them via
     *  [observePendingInvites] on their own device. */
    suspend fun inviteByEmail(email: String): Result<Unit>

    /** Unauthenticated-safe preview of a link's invite, shown before forcing sign-in. */
    suspend fun previewInvite(token: String): Result<PendingHouseholdInvite>

    /** The link path: accept by raw token. */
    suspend fun joinWithToken(token: String): Result<HouseholdJoinOutcome>

    /** The in-app path: accept a pending invite already visible via [observePendingInvites], by id
     *  — no token involved, since the pending-invite listing never carries one. */
    suspend fun acceptInvite(inviteId: UUID): Result<HouseholdJoinOutcome>

    suspend fun declineInvite(inviteId: UUID): Result<Unit>
}
