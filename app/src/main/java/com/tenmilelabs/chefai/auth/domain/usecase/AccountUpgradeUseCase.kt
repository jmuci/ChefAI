package com.tenmilelabs.chefai.auth.domain.usecase

import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.UserEntity
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
    private val recipeLabelCrossRefDao: RecipeLabelCrossRefDao
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
        if (recipeCount == 0) {
            Timber.d("No anonymous recipes to upgrade (new device or empty). Creating auth user only.")
            ensureAuthenticatedUserEntity(authenticatedUser)
            return 0
        }

        // Run the entire upgrade in a single transaction
        return transactionRunner.withTransaction {
            val now = System.currentTimeMillis()

            // Step 1: Create the authenticated UserEntity first (FK target must exist)
            ensureAuthenticatedUserEntity(authenticatedUser)

            // Step 2: Get recipe IDs before reassignment (needed for child entity updates)
            val recipeIds = recipeDao.getRecipeIdsForUser(anonymousUserId)

            // Step 3: Reassign all recipes from anonymous to authenticated + mark PENDING
            recipeDao.reassignCreatorAndMarkPending(
                oldCreatorId = anonymousUserId,
                newCreatorId = authenticatedUser.uuid,
                updatedAt = now
            )

            // Step 4: Mark all child entities as PENDING for sync
            if (recipeIds.isNotEmpty()) {
                recipeStepDao.markPendingForRecipes(recipeIds, now)
                recipeIngredientDao.markPendingForRecipes(recipeIds, now)
                recipeTagCrossRefDao.markPendingForRecipes(recipeIds, now)
                recipeLabelCrossRefDao.markPendingForRecipes(recipeIds, now)
            }

            // Step 5: Delete the anonymous UserEntity (no recipes point to it anymore)
            userDao.deleteUser(anonymousUserId)

            Timber.d("Account upgrade complete: $recipeCount recipes reassigned")
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
