package com.tenmilelabs.chefai.auth.domain

import com.tenmilelabs.chefai.auth.data.local.SecurePreferencesInterface
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.BookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.ChefAIDataBase
import com.tenmilelabs.chefai.core.data.local.room.dao.HouseholdDao
import com.tenmilelabs.chefai.core.data.local.room.dao.MealPlanDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeCookEventDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.SavedMealPlanDao
import com.tenmilelabs.chefai.core.data.local.room.dao.SyncMetadataDao
import com.tenmilelabs.chefai.core.data.local.room.dao.UserDao
import com.tenmilelabs.chefai.mealplans.data.local.SavedPlanImageStore
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
    private val householdDao: HouseholdDao,
    private val mealPlanDao: MealPlanDao,
    private val savedMealPlanDao: SavedMealPlanDao,
    private val bookmarkedRecipeDao: BookmarkedRecipeDao,
    private val recipeCookEventDao: RecipeCookEventDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val transactionRunner: TransactionRunner,
    private val recipeImageStore: RecipeImageStore,
    private val savedPlanImageStore: SavedPlanImageStore,
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
                Timber.i("Authenticated account changed from $previousUserId to $newUserId while anonymous data exists, removing previous authenticated user data only")
                // Keep everything the anonymous session owns (the upgrade that follows moves it to
                // the new account) and drop everything the departing account left behind; the
                // server restores whatever had synced when that account signs in again.
                //
                // Keeping the departing account's rows used to look harmless because most reads
                // filter by userId, but two things didn't: cook events have no user column (the
                // incoming account saw the departing one's "Cooked before" history), and the push
                // collects meal plans, saved plans and cook events without a user filter (they
                // were sent under the incoming account's token). Edits the departing account
                // still hadn't pushed are lost here; LogoutUseCase tries to push them before the
                // session ends, so this is only what that best-effort push couldn't send.
                //
                // Ids are collected first because the deletes are bulk SQL — after they run there
                // is no way to tell which image files belonged to the departing account.
                // deleteAll() would be wrong here, since this branch exists precisely to preserve
                // the anonymous session's own images.
                val (departingRecipeIds, departingSavedPlanIds) = transactionRunner {
                    val recipeIds = recipeDao.getRecipeIdsForUser(previousUserId)
                    val savedPlanIds = savedMealPlanDao.getIdsNotOwnedBy(anonymousUserId)
                    recipeDao.deleteRecipesForUser(previousUserId)
                    recipeCookEventDao.deleteAllNotOnPlansOf(anonymousUserId)
                    mealPlanDao.deleteAllNotOwnedBy(anonymousUserId)
                    savedMealPlanDao.deleteAllNotOwnedBy(anonymousUserId)
                    bookmarkedRecipeDao.deleteAllNotOwnedBy(anonymousUserId)
                    // The pull cursor is the departing account's: kept, the incoming account's
                    // first pull would skip everything it changed before that moment.
                    syncMetadataDao.clear()
                    recipeIds to savedPlanIds
                }
                departingRecipeIds.forEach { recipeImageStore.delete(it) }
                departingSavedPlanIds.forEach { savedPlanImageStore.delete(it) }
                // Unlike recipes/meal plans, the household cache has no userId column to filter
                // by (see HouseholdEntity's doc) — it's a single global "my household" mirror, so
                // leaving it in place would hand the incoming account the departing one's
                // membership until the next refresh happens to complete. That refresh runs
                // asynchronously off the new session (SessionManager.attachHouseholdState), so a
                // caller reading the cache in the window before it lands — e.g.
                // AcceptInviteViewModel's own-household check right after this same sign-up —
                // would see the departing account's household and misreport the new one as already
                // a member of it.
                householdDao.clearCache()
                AccountSwitchOutcome.PRESERVED_ANONYMOUS_DATA
            }
            else -> {
            Timber.i("Authenticated account changed from $previousUserId to $newUserId, clearing local database")
            database.clearAllTables()
                recipeImageStore.deleteAll()
                savedPlanImageStore.deleteAll()
                AccountSwitchOutcome.CLEARED_DATABASE
            }
        }

        securePreferences.setCurrentUserId(newUserId)
        return outcome
    }

    /**
     * Wipes every table and every cached recipe image — used after the signed-in account was
     * deleted server-side, so nothing of it stays on the device. That includes other creators'
     * cached recipes and unsaved import drafts: they can be re-fetched or redone, and a full wipe
     * can't miss a table the way a per-user delete could.
     *
     * Blocking: Room's `clearAllTables` refuses the main thread, so call this off it.
     */
    suspend fun clearAllLocalData() {
        Timber.i("Clearing all local data after account deletion")
        database.clearAllTables()
        recipeImageStore.deleteAll()
        savedPlanImageStore.deleteAll()
    }
}
