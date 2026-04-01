package com.tenmilelabs.chefai.auth.domain.usecase

import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
import com.tenmilelabs.chefai.core.data.local.room.dao.BookmarkedRecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.MealPlanDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.UserDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.domain.model.User
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the database upgrade when an anonymous user authenticates (registers or logs in).
 *
 * Reassigns all local recipes from the anonymous userId to the authenticated userId,
 * marks all entities as PENDING for sync, creates the authenticated UserEntity in Room,
 * and deletes the anonymous UserEntity.
 *
 * The entire operation runs in a single Room transaction for atomicity.
 *
 * @see <a href="docs/rfcs/rfc-001-offline-first-sync.md">RFC-001 Section 7</a>
 */
@Singleton
class AccountUpgradeUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val userDao: UserDao,
    private val recipeDao: RecipeDao,
    private val recipeStepDao: RecipeStepDao,
    private val recipeIngredientDao: RecipeIngredientDao,
    private val recipeTagCrossRefDao: RecipeTagCrossRefDao,
    private val recipeLabelCrossRefDao: RecipeLabelCrossRefDao,
    private val bookmarkedRecipeDao: BookmarkedRecipeDao,
    private val mealPlanDao: MealPlanDao,
) {

    /**
     * Executes the anonymous → authenticated data upgrade.
     *
     * @param anonymousUserId The local anonymous UUID whose recipes should be reassigned.
     * @param authenticatedUser The authenticated user from the backend response.
     * @return The number of recipes reassigned, or 0 if there were none (new device scenario).
     */
    suspend fun execute(anonymousUserId: UUID, authenticatedUser: User): Int {
        Timber.d("Starting account upgrade: anon=$anonymousUserId -> auth=${authenticatedUser.uuid}")

        // If the anonymous ID equals the authenticated ID (edge case), nothing to do
        if (anonymousUserId == authenticatedUser.uuid) {
            Timber.d("Anonymous and authenticated IDs match. Skipping upgrade.")
            return 0
        }

        val recipeCount = recipeDao.countRecipesForUser(anonymousUserId)
        val mealPlanCount = mealPlanDao.countMealPlansForUser(anonymousUserId)

        if (recipeCount == 0 && mealPlanCount == 0) {
            Timber.d("No anonymous data to upgrade (new device or empty). Creating auth user only.")
            ensureAuthenticatedUserEntity(authenticatedUser)
            return 0
        }

        // Run the entire upgrade in a single transaction
        return transactionRunner.invoke {
            val now = System.currentTimeMillis()

            // Step 1: Create the authenticated UserEntity first (FK target must exist)
            ensureAuthenticatedUserEntity(authenticatedUser)

            // Step 2: Reassign recipes (and their child entities) if any exist
            if (recipeCount > 0) {
                val recipeIds = recipeDao.getRecipeIdsForUser(anonymousUserId)
                recipeDao.reassignCreatorAndMarkPending(
                    oldCreatorId = anonymousUserId,
                    newCreatorId = authenticatedUser.uuid,
                    updatedAt = now
                )
                if (recipeIds.isNotEmpty()) {
                    recipeStepDao.markPendingForRecipes(recipeIds, now)
                    recipeIngredientDao.markPendingForRecipes(recipeIds, now)
                    recipeTagCrossRefDao.markPendingForRecipes(recipeIds, now)
                    recipeLabelCrossRefDao.markPendingForRecipes(recipeIds, now)
                }
            }

            // Step 3: Reassign bookmarks from anonymous to authenticated user
            bookmarkedRecipeDao.reassignUserAndMarkPending(
                oldUserId = anonymousUserId,
                newUserId = authenticatedUser.uuid,
                updatedAt = now
            )

            // Step 4: Reassign meal plans from anonymous to authenticated user
            mealPlanDao.reassignUserAndMarkPending(
                oldUserId = anonymousUserId,
                newUserId = authenticatedUser.uuid,
                updatedAt = now
            )

            // Step 5: Delete the anonymous UserEntity (nothing points to it anymore)
            userDao.deleteUser(anonymousUserId)

            Timber.d("Account upgrade complete: $recipeCount recipes, $mealPlanCount meal plans reassigned")
            recipeCount
        }
    }

    private suspend fun ensureAuthenticatedUserEntity(user: User) {
        val existing = userDao.getUserById(user.uuid)
        if (existing == null) {
            userDao.upsertUser(
                UserEntity(
                    uuid = user.uuid,
                    displayName = user.displayName,
                    email = user.email,
                    avatarUrl = user.avatarUrl,
                    updatedAt = System.currentTimeMillis(),
                    deletedAt = null,
                    syncState = SyncState.SYNCED // Auth user is server-authoritative
                )
            )
            Timber.d("Created authenticated UserEntity: ${user.uuid}")
        }
    }
}
