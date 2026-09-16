package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.HouseholdEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdInviteEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdMemberEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Backs the read-through household cache — see [HouseholdEntity]'s doc for why these tables are
 * wholesale-replaced on refresh rather than pushed/pulled through the sync protocol.
 */
@Dao
interface HouseholdDao {

    @Query("SELECT * FROM households WHERE uuid = :id")
    fun observeHousehold(id: UUID): Flow<HouseholdEntity?>

    /** At most one row ever exists (see [HouseholdEntity]'s doc) — the reactive counterpart of
     *  [getCachedHouseholdId], needing no id to observe "whichever household is cached". */
    @Query("SELECT * FROM households LIMIT 1")
    fun observeCachedHousehold(): Flow<HouseholdEntity?>

    /** Resolves "my household id" for callers that need it before they can query the id-scoped
     *  methods above. */
    @Query("SELECT uuid FROM households LIMIT 1")
    suspend fun getCachedHouseholdId(): UUID?

    @Query("SELECT * FROM household_members WHERE householdId = :id ORDER BY role ASC, displayName ASC")
    fun observeMembers(id: UUID): Flow<List<HouseholdMemberEntity>>

    @Query("SELECT * FROM household_invites ORDER BY createdAt DESC")
    fun observePendingInvites(): Flow<List<HouseholdInviteEntity>>

    /** One-shot read for [com.tenmilelabs.chefai.household.data.repository
     *  .DefaultHouseholdRepository.refresh] to diff the fresh server listing against. */
    @Query("SELECT inviteId FROM household_invites")
    suspend fun getCachedInviteIds(): List<UUID>

    @Upsert
    suspend fun upsertHousehold(household: HouseholdEntity)

    @Upsert
    suspend fun upsertMembers(members: List<HouseholdMemberEntity>)

    @Upsert
    suspend fun upsertInvites(invites: List<HouseholdInviteEntity>)

    @Query("DELETE FROM household_members WHERE householdId = :id")
    suspend fun clearMembers(id: UUID)

    @Query("DELETE FROM household_invites WHERE inviteId = :inviteId")
    suspend fun removeInvite(inviteId: UUID)

    @Query("DELETE FROM household_invites WHERE householdId = :id")
    suspend fun clearInvitesForHousehold(id: UUID)

    @Query("DELETE FROM households WHERE uuid = :id")
    suspend fun deleteHouseholdRow(id: UUID)

    /**
     * Full local teardown on leave/removal — the household ceases to be this device's, along with
     * its cached members and any invites addressed under it. `@Transaction` composed from three
     * plain deletes (same idiom as [com.tenmilelabs.chefai.core.data.local.room.dao
     * .RecipeIngredientDao.upsertAllForRecipe]) rather than one, since there's no local FK to
     * cascade this — see [HouseholdEntity]'s doc for why these tables can't be an FK target.
     */
    @Transaction
    suspend fun deleteHousehold(id: UUID) {
        deleteHouseholdRow(id)
        clearMembers(id)
        clearInvitesForHousehold(id)
    }
}
