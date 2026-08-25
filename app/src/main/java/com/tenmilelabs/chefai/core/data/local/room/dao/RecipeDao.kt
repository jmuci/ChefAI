package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.PlanIngredientRow
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeImageCandidate
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeImageUploadCandidate
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeWithDetails
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeWithLabels
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeWithTags
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the recipes table.
 */
@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes WHERE creatorId = :creatorId AND deletedAt IS NULL")
    fun observeAllRecipesForUser(creatorId: UUID): Flow<List<RecipeEntity>>

    @Query("""
        SELECT
            ri.ingredientId AS ingredientId,
            i.displayName AS ingredientDisplayName,
            ri.quantity AS quantity,
            ri.unit AS unit,
            a.displayName AS allergenName,
            s.category AS srcCategory,
            s.subcategory AS srcSubcategory
        FROM recipe_ingredients AS ri
        INNER JOIN ingredients AS i ON ri.ingredientId = i.uuid
        LEFT JOIN allergens AS a ON i.allergenId = a.uuid
        LEFT JOIN source_classifications AS s ON i.sourcePrimaryId = s.uuid
        WHERE ri.recipeId = :recipeId
    """)
    fun observeIngredientsForRecipe(recipeId: UUID): Flow<List<RecipeIngredient>>

    /**
     * Ingredients for a batch of recipes in one query, for the meal-plan shopping list.
     *
     * Unlike [observeIngredientsForRecipe] this filters soft-deleted rows out on all three tables:
     * a shopping list must not send you out for something belonging to a deleted recipe.
     */
    @Query("""
        SELECT
            ri.recipeId AS recipeId,
            r.servings AS recipeServings,
            ri.ingredientId AS ingredientId,
            i.displayName AS ingredientDisplayName,
            ri.quantity AS quantity,
            ri.unit AS unit
        FROM recipe_ingredients AS ri
        INNER JOIN recipes AS r ON ri.recipeId = r.uuid
        INNER JOIN ingredients AS i ON ri.ingredientId = i.uuid
        WHERE ri.recipeId IN (:recipeIds)
          AND ri.deletedAt IS NULL
          AND r.deletedAt IS NULL
          AND i.deletedAt IS NULL
    """)
    fun observeIngredientsForRecipes(recipeIds: List<UUID>): Flow<List<PlanIngredientRow>>

    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    suspend fun getRecipeById(uuid: UUID): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    fun observeRecipeById(uuid: UUID): Flow<RecipeEntity?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE uuid = :uuid AND deletedAt IS NULL")
    suspend fun getRecipeWithDetails(uuid: UUID): RecipeWithDetails?

    @Transaction
    @Query("SELECT * FROM recipes WHERE uuid = :uuid AND deletedAt IS NULL")
    fun observeRecipeWithDetails(uuid: UUID): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE deletedAt IS NULL")
    fun observeRecipesWithDetails(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE creatorId = :creatorId AND deletedAt IS NULL")
    fun observeRecipesWithDetailsForUser(creatorId: UUID): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE privacy = 'PUBLIC' AND deletedAt IS NULL")
    fun observePublicRecipesWithDetails(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE uuid = :uuid AND deletedAt IS NULL")
    suspend fun getRecipeWithTags(uuid: UUID): RecipeWithTags?

    @Transaction
    @Query("SELECT * FROM recipes WHERE deletedAt IS NULL")
    fun observeRecipesWithTags(): Flow<List<RecipeWithTags>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE uuid = :uuid AND deletedAt IS NULL")
    suspend fun getRecipeWithLabels(uuid: UUID): RecipeWithLabels?

    @Transaction
    @Query("SELECT * FROM recipes WHERE deletedAt IS NULL")
    fun observeRecipesWithLabels(): Flow<List<RecipeWithLabels>>

    @Query("DELETE FROM recipes WHERE uuid = :uuid")
    suspend fun deleteRecipe(uuid: UUID)

    @Query("DELETE FROM recipes")
    suspend fun deleteAllRecipes()

    @Query("DELETE FROM recipes WHERE creatorId = :creatorId")
    suspend fun deleteRecipesForUser(creatorId: UUID)

    @Upsert
    suspend fun upsertRecipe(recipe: RecipeEntity)

    @Upsert
    suspend fun upsertAll(recipes: List<RecipeEntity>)

    @Query("SELECT * FROM recipes WHERE syncState = 'PENDING'")
    suspend fun getDirty(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE syncState IN ('PENDING', 'DELETED')")
    suspend fun getAllDirty(): List<RecipeEntity>

    @Query("UPDATE recipes SET syncState = :syncState, updatedAt = :updatedAt WHERE uuid = :uuid")
    suspend fun updateSyncState(uuid: UUID, syncState: SyncState, updatedAt: Long)

    @Query("UPDATE recipes SET deletedAt = :deletedAt, syncState = 'DELETED', updatedAt = :deletedAt WHERE uuid = :uuid")
    suspend fun softDelete(uuid: UUID, deletedAt: Long)

    // --- Image backfill ---

    /**
     * Recipes that could still be missing their on-device image, newest first.
     *
     * A recipe qualifies if its image can be obtained from *somewhere*: a source URL to re-derive
     * from, or a blob the backend already holds.
     *
     * `imageUrl != ''` alone used to be the whole predicate, and it deliberately excluded a user's
     * own photo — a picked image has no source URL, so re-deriving it is impossible and the editor
     * clears `imageUrl` precisely so this predicate can tell the two apart (ADR-011 Decision 3).
     * `OR imageBlobId IS NOT NULL` is what finally lets those photos be restored on a second device:
     * once they are uploaded there *is* somewhere to fetch them from. This is the whole point of
     * Stage 2.
     *
     * Recipes that have already failed [maxAttempts] times are excluded so a permanently dead URL
     * stops costing work.
     *
     * Whether the file is actually *present* isn't expressible in SQL, so this deliberately
     * over-selects and the caller filters — see `RecipeImageBackfillWorker`.
     */
    @Query(
        """
        SELECT r.uuid AS recipeId, r.imageUrl AS imageUrl, r.localImagePath AS localImagePath,
               r.imageBlobId AS imageBlobId
        FROM recipes r
        LEFT JOIN recipe_image_state s ON s.recipeId = r.uuid
        WHERE r.deletedAt IS NULL
          AND (r.imageUrl != '' OR r.imageBlobId IS NOT NULL)
          AND COALESCE(s.attempts, 0) < :maxAttempts
        ORDER BY r.updatedAt DESC
        LIMIT :scanLimit
        """
    )
    suspend fun getImageBackfillCandidates(maxAttempts: Int, scanLimit: Int): List<RecipeImageCandidate>

    @Query("UPDATE recipes SET localImagePath = :localImagePath WHERE uuid = :uuid")
    suspend fun updateLocalImagePath(uuid: UUID, localImagePath: String?)

    /**
     * Recipes whose cached image may not be on the backend yet.
     *
     * `syncState = 'SYNCED'` is doing real work: the recipe row has to exist server-side before its
     * image can be attached to it, so this is what makes the upload unable to 404 and removes any
     * ordering question between the two.
     *
     * User photos are ordered first because they are the only images that cannot be re-derived from
     * anywhere — a blank `imageUrl` means exactly that (ADR-011 Decision 3). If a sweep only gets
     * through part of its batch, the irreplaceable bytes are the ones that made it.
     *
     * Deliberately over-selects rows that already have an `imageBlobId`: whether the file on disk is
     * still the one that was uploaded isn't expressible in SQL. The caller narrows — see
     * `RecipeImageUploadWorker`.
     */
    @Query(
        """
        SELECT r.uuid AS recipeId, r.localImagePath AS localImagePath,
               r.imageBlobId AS imageBlobId, s.uploadedFileModifiedAt AS uploadedFileModifiedAt
        FROM recipes r
        LEFT JOIN recipe_image_state s ON s.recipeId = r.uuid
        WHERE r.deletedAt IS NULL
          AND r.localImagePath IS NOT NULL
          AND r.syncState = 'SYNCED'
          AND COALESCE(s.uploadAttempts, 0) < :maxAttempts
        ORDER BY (r.imageUrl = '') DESC, r.updatedAt DESC
        LIMIT :scanLimit
        """
    )
    suspend fun getImageUploadCandidates(
        maxAttempts: Int,
        scanLimit: Int,
    ): List<RecipeImageUploadCandidate>

    /**
     * Records the blob the backend stored for this recipe.
     *
     * A targeted UPDATE, not a row write: the value came *from* the server, so it must not mark the
     * row PENDING or move `updatedAt` — doing either would push a change the server already has and
     * churn the pull delta forever.
     */
    @Query("UPDATE recipes SET imageBlobId = :imageBlobId WHERE uuid = :uuid")
    suspend fun updateImageBlobId(uuid: UUID, imageBlobId: String?)

    // --- Account upgrade queries ---

    @Query("UPDATE recipes SET creatorId = :newCreatorId, syncState = 'PENDING', updatedAt = :updatedAt WHERE creatorId = :oldCreatorId")
    suspend fun reassignCreatorAndMarkPending(oldCreatorId: UUID, newCreatorId: UUID, updatedAt: Long)

    @Query("SELECT uuid FROM recipes WHERE creatorId = :creatorId")
    suspend fun getRecipeIdsForUser(creatorId: UUID): List<UUID>

    @Query("SELECT COUNT(*) FROM recipes WHERE creatorId = :creatorId AND deletedAt IS NULL")
    suspend fun countRecipesForUser(creatorId: UUID): Int

    // --- Search (offline/anonymous fallback — see RecipeSearchRepository) ---

    /**
     * Plain `LIKE` scan over title plus tag/label display names — the fallback used when the
     * network search is unreachable (offline) or unavailable (anonymous sessions have no JWT).
     * No FTS4, no ranking, no stemming: at the local corpus size (a single device's synced
     * recipes) a `LIKE` scan is sub-millisecond, and this is a deliberate degradation the UI
     * flags to the user, not a second source of truth to keep polished.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM recipes r
        WHERE r.deletedAt IS NULL
          AND (
            r.title LIKE '%' || :query || '%'
            OR EXISTS (
                SELECT 1 FROM recipe_tags rt
                INNER JOIN tags t ON t.uuid = rt.tagId
                WHERE rt.recipeId = r.uuid AND rt.deletedAt IS NULL AND t.displayName LIKE '%' || :query || '%'
            )
            OR EXISTS (
                SELECT 1 FROM recipe_labels rl
                INNER JOIN labels l ON l.uuid = rl.labelId
                WHERE rl.recipeId = r.uuid AND rl.deletedAt IS NULL AND l.displayName LIKE '%' || :query || '%'
            )
          )
        ORDER BY r.updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchRecipesWithDetails(query: String, limit: Int): List<RecipeWithDetails>

}
