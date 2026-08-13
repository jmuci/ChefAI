package com.tenmilelabs.chefai.auth.domain

import com.tenmilelabs.chefai.auth.data.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.UserDao
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class AccountSwitchOutcome {
    NO_CHANGE,
    PRESERVED_ANONYMOUS_DATA,
    CLEARED_DATABASE
}

/**
 * Clears local user-scoped data when a different authenticated account logs in on this device.
 */
@Singleton
class AccountSwitchHandler @Inject constructor(
    private val securePreferences: SecurePreferencesInterface,
    private val database: ChefAIDataBase,
    private val recipeDao: RecipeDao,
    private val userDao: UserDao,
    private val recipeImageStore: RecipeImageStore,
) {

    /**
     * Preserves current anonymous data when switching accounts from an anonymous session,
     * but removes stale data from the previous authenticated account.
     */
    suspend fun handleLogin(newUserId: UUID, anonymousUserId: UUID? = null): AccountSwitchOutcome {
        val previousUserId = securePreferences.getStoredCurrentUserId().first()
        val isAccountSwitch = previousUserId != null && previousUserId != newUserId

        val outcome = when {
            !isAccountSwitch -> {
                Timber.d("Authenticated account unchanged or first login, keeping local database for user: $newUserId")
                AccountSwitchOutcome.NO_CHANGE
            }
            anonymousUserId != null -> {
                Timber.i("Authenticated account changed from $previousUserId to $newUserId while anonymous data exists, removing previous authenticated user recipes only")
                // Delete only recipes (server can restore them on next sync).
                // We intentionally keep the UserEntity and its meal plans: all queries
                // filter by userId so they're invisible to other users, and when the
                // original user logs back in their local plans will still be there.
                recipeDao.deleteRecipesForUser(previousUserId)
                AccountSwitchOutcome.PRESERVED_ANONYMOUS_DATA
            }
            else -> {
            Timber.i("Authenticated account changed from $previousUserId to $newUserId, clearing local database")
            database.clearAllTables()
                recipeImageStore.deleteAll()
                AccountSwitchOutcome.CLEARED_DATABASE
            }
        }

        securePreferences.setCurrentUserId(newUserId)
        return outcome
    }
}
