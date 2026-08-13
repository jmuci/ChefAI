package com.tenmilelabs.chefai.auth.domain

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.auth.data.local.FakeSecurePreferences
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeUserDao
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitchHandlerTest {

    private lateinit var securePreferences: FakeSecurePreferences
    private lateinit var database: ChefAIDataBase
    private lateinit var recipeDao: FakeRecipeDao
    private lateinit var userDao: FakeUserDao
    private lateinit var recipeImageStore: RecipeImageStore
    private lateinit var handler: AccountSwitchHandler

    @Before
    fun setup() {
        securePreferences = FakeSecurePreferences()
        database = mockk(relaxed = true)
        recipeDao = FakeRecipeDao()
        userDao = FakeUserDao()
        recipeImageStore = mockk(relaxed = true)
        handler = AccountSwitchHandler(
            securePreferences = securePreferences,
            database = database,
            recipeDao = recipeDao,
            userDao = userDao,
            recipeImageStore = recipeImageStore,
        )
    }

    @Test
    fun `first authenticated login keeps database and stores user id`() = runTest {
        val userId = UuidV7Generator.newId()

        val cleared = handler.handleLogin(userId)

        assertThat(cleared).isEqualTo(AccountSwitchOutcome.NO_CHANGE)
        assertThat(securePreferences.getStoredCurrentUserId().first()).isEqualTo(userId)
        coVerify(exactly = 0) { database.clearAllTables() }
    }

    @Test
    fun `same authenticated user keeps database`() = runTest {
        val userId = UuidV7Generator.newId()
        securePreferences.setCurrentUserId(userId)

        val cleared = handler.handleLogin(userId)

        assertThat(cleared).isEqualTo(AccountSwitchOutcome.NO_CHANGE)
        coVerify(exactly = 0) { database.clearAllTables() }
    }

    @Test
    fun `different authenticated user without anonymous session clears database and stores replacement user id`() = runTest {
        securePreferences.setCurrentUserId(UuidV7Generator.newId())
        val newUserId = UuidV7Generator.newId()

        val cleared = handler.handleLogin(newUserId)

        assertThat(cleared).isEqualTo(AccountSwitchOutcome.CLEARED_DATABASE)
        assertThat(securePreferences.getStoredCurrentUserId().first()).isEqualTo(newUserId)
        coVerify(exactly = 1) { database.clearAllTables() }
        coVerify(exactly = 1) { recipeImageStore.deleteAll() }
    }

    @Test
    fun `different authenticated user with anonymous session preserves anonymous data path`() = runTest {
        securePreferences.setCurrentUserId(UuidV7Generator.newId())
        val anonymousUserId = UuidV7Generator.newId()
        val newUserId = UuidV7Generator.newId()

        val outcome = handler.handleLogin(
            newUserId = newUserId,
            anonymousUserId = anonymousUserId
        )

        assertThat(outcome).isEqualTo(AccountSwitchOutcome.PRESERVED_ANONYMOUS_DATA)
        assertThat(securePreferences.getStoredCurrentUserId().first()).isEqualTo(newUserId)
        coVerify(exactly = 0) { database.clearAllTables() }
        coVerify(exactly = 0) { recipeImageStore.deleteAll() }
    }
}
