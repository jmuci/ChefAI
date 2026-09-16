package com.tenmilelabs.chefai.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.HouseholdEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdInviteEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdMemberEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented test for [com.tenmilelabs.chefai.core.data.local.room.dao.HouseholdDao] against a
 * real (in-memory) Room database. This cache is wholesale-replaced on every refresh rather than
 * pushed/pulled through sync — see [HouseholdEntity]'s doc — so the assertions here focus on that
 * replace-on-refresh idiom (`clearMembers` + reinsert) rather than any dirty-queue behaviour.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class HouseholdDaoTest {

    private lateinit var database: ChefAIDataBase

    private fun household(uuid: UUID = UuidV7Generator.newId(), ownerId: UUID = UuidV7Generator.newId()) =
        HouseholdEntity(uuid = uuid, name = "The Test Kitchen", ownerId = ownerId, createdAt = 0L, updatedAt = 0L)

    private fun member(householdId: UUID, userId: UUID = UuidV7Generator.newId(), role: String = "MEMBER") =
        HouseholdMemberEntity(
            householdId = householdId,
            userId = userId,
            displayName = "Chef $userId",
            avatarUrl = "",
            role = role,
            joinedAt = 0L,
        )

    private fun invite(householdId: UUID, inviteId: UUID = UuidV7Generator.newId()) = HouseholdInviteEntity(
        inviteId = inviteId,
        householdId = householdId,
        expiresAt = 999_999L,
        createdAt = 1_000L,
    )

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun upsertHousehold_thenObserveHousehold_returnsIt() = runTest {
        val entity = household()
        database.householdDao().upsertHousehold(entity)

        assertEquals(entity, database.householdDao().observeHousehold(entity.uuid).first())
    }

    @Test
    fun observeHousehold_returnsNullForAnUnknownId() = runTest {
        assertNull(database.householdDao().observeHousehold(UuidV7Generator.newId()).first())
    }

    @Test
    fun upsertMembers_scopesMembersToTheirOwnHousehold() = runTest {
        val householdA = household()
        val householdB = household()
        database.householdDao().upsertHousehold(householdA)
        database.householdDao().upsertHousehold(householdB)

        val memberA = member(householdA.uuid)
        val memberB = member(householdB.uuid)
        database.householdDao().upsertMembers(listOf(memberA, memberB))

        assertEquals(listOf(memberA), database.householdDao().observeMembers(householdA.uuid).first())
        assertEquals(listOf(memberB), database.householdDao().observeMembers(householdB.uuid).first())
    }

    @Test
    fun clearMembers_thenReinsert_replacesTheRosterWholesale() = runTest {
        // This is the exact refresh idiom DefaultHouseholdRepository uses: a member who is no
        // longer in the server's roster must disappear locally too, not just get left stale.
        val entity = household()
        database.householdDao().upsertHousehold(entity)
        val stale = member(entity.uuid)
        database.householdDao().upsertMembers(listOf(stale))

        database.householdDao().clearMembers(entity.uuid)
        val fresh = member(entity.uuid)
        database.householdDao().upsertMembers(listOf(fresh))

        assertEquals(listOf(fresh), database.householdDao().observeMembers(entity.uuid).first())
    }

    @Test
    fun upsertInvites_thenObservePendingInvites_returnsThem() = runTest {
        val entity = household()
        database.householdDao().upsertHousehold(entity)
        val pending = invite(entity.uuid)

        database.householdDao().upsertInvites(listOf(pending))

        assertEquals(listOf(pending), database.householdDao().observePendingInvites().first())
    }

    @Test
    fun removeInvite_removesOnlyThatInvite() = runTest {
        val entity = household()
        database.householdDao().upsertHousehold(entity)
        val keep = invite(entity.uuid)
        val resolved = invite(entity.uuid)
        database.householdDao().upsertInvites(listOf(keep, resolved))

        database.householdDao().removeInvite(resolved.inviteId)

        assertEquals(listOf(keep), database.householdDao().observePendingInvites().first())
    }

    @Test
    fun deleteHousehold_removesTheHouseholdRow() = runTest {
        val entity = household()
        database.householdDao().upsertHousehold(entity)

        database.householdDao().deleteHousehold(entity.uuid)

        assertNull(database.householdDao().observeHousehold(entity.uuid).first())
    }

    @Test
    fun deleteHousehold_isAFullLocalTeardownOfItsOwnMembersAndInvites() = runTest {
        // No FK from household_members/household_invites to households (see HouseholdEntity's
        // doc), so deleteHousehold composes three plain deletes under @Transaction itself rather
        // than relying on a cascade — this pins that composition down.
        val entity = household()
        database.householdDao().upsertHousehold(entity)
        database.householdDao().upsertMembers(listOf(member(entity.uuid)))
        database.householdDao().upsertInvites(listOf(invite(entity.uuid)))

        database.householdDao().deleteHousehold(entity.uuid)

        assertTrue(database.householdDao().observeMembers(entity.uuid).first().isEmpty())
        assertTrue(database.householdDao().observePendingInvites().first().isEmpty())
    }

    @Test
    fun deleteHousehold_leavesMembersOfOtherHouseholdsUntouched() = runTest {
        // deleteHousehold only ever composes deletes scoped to its own [id] — this pins that
        // scoping down so a future edit to the @Transaction body can't widen it by accident.
        val entity = household()
        val other = household()
        database.householdDao().upsertHousehold(entity)
        database.householdDao().upsertHousehold(other)
        val otherMember = member(other.uuid)
        database.householdDao().upsertMembers(listOf(otherMember))

        database.householdDao().deleteHousehold(entity.uuid)

        assertTrue(database.householdDao().observeMembers(other.uuid).first().contains(otherMember))
    }
}
