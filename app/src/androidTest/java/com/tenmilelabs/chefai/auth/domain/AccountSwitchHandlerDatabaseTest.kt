package com.tenmilelabs.chefai.auth.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.HouseholdEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdInviteEntity
import com.tenmilelabs.chefai.core.data.local.room.HouseholdMemberEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented: pins down that [AccountSwitchHandler]'s account-switch `database.clearAllTables()`
 * path clears the household tables too — see ADR-014. Room's `clearAllTables()` already covers
 * every table registered on the `@Database` by construction, so nothing in `AccountSwitchHandler`
 * itself needed to change when households were added; this test exists purely so that invariant is
 * pinned down rather than relying on it staying true by accident.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class AccountSwitchHandlerDatabaseTest {

    private lateinit var database: ChefAIDataBase
    private lateinit var handler: AccountSwitchHandler
    private lateinit var securePreferences: FakeSecurePreferences

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(getApplicationContext(), ChefAIDataBase::class.java)
            .allowMainThreadQueries()
            .build()
        securePreferences = FakeSecurePreferences()
        handler = AccountSwitchHandler(
            securePreferences = securePreferences,
            database = database,
            recipeDao = database.recipeDao(),
            userDao = database.userDao(),
            recipeImageStore = RecipeImageStore(getApplicationContext(), Dispatchers.IO),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun accountSwitchClearsHouseholdTables() = runTest {
        val previousUserId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(previousUserId)

        val household = HouseholdEntity(
            uuid = UuidV7Generator.newId(),
            name = "The Test Kitchen",
            ownerId = previousUserId,
            createdAt = 0L,
            updatedAt = 0L,
        )
        database.householdDao().upsertHousehold(household)
        database.householdDao().upsertMembers(
            listOf(
                HouseholdMemberEntity(
                    householdId = household.uuid,
                    userId = previousUserId,
                    displayName = "Chef",
                    avatarUrl = "",
                    role = "OWNER",
                    joinedAt = 0L,
                )
            )
        )
        database.householdDao().upsertInvites(
            listOf(
                HouseholdInviteEntity(
                    inviteId = UuidV7Generator.newId(),
                    householdId = household.uuid,
                    expiresAt = 999_999L,
                    createdAt = 1_000L,
                )
            )
        )

        // A different account logging in with no anonymous session takes the full-clear path.
        handler.handleLogin(newUserId = UuidV7Generator.newId())

        assertNull(database.householdDao().observeHousehold(household.uuid).first())
        assertTrue(database.householdDao().observeMembers(household.uuid).first().isEmpty())
        assertTrue(database.householdDao().observePendingInvites().first().isEmpty())
    }
}
