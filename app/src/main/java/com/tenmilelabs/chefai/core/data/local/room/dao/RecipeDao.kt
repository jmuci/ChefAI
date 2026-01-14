package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeWithDetails
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeWithLabels
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeWithTags
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the recipes table.
 */
@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes WHERE creatorId = :creatorId")
    fun observeAllRecipesForUser(creatorId: UUID): Flow<List<RecipeEntity>>

    @Query(
        """
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
    """
    )
    fun observeIngredientsForRecipe(recipeId: UUID): Flow<List<RecipeIngredient>>

    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    suspend fun getRecipeById(uuid: UUID): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    fun observeRecipeById(uuid: UUID): Flow<RecipeEntity?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    suspend fun getRecipeWithDetails(uuid: UUID): RecipeWithDetails?

    @Transaction
    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    fun observeRecipeWithDetails(uuid: UUID): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes")
    fun observeRecipesWithDetails(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE creatorId = :creatorId")
    fun observeRecipesWithDetailsForUser(creatorId: UUID): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    suspend fun getRecipeWithTags(uuid: UUID): RecipeWithTags?

    @Transaction
    @Query("SELECT * FROM recipes")
    fun observeRecipesWithTags(): Flow<List<RecipeWithTags>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE uuid = :uuid")
    suspend fun getRecipeWithLabels(uuid: UUID): RecipeWithLabels?

    @Transaction
    @Query("SELECT * FROM recipes")
    fun observeRecipesWithLabels(): Flow<List<RecipeWithLabels>>

    @Query("DELETE FROM recipes WHERE uuid = :uuid")
    suspend fun deleteRecipe(uuid: UUID)

    @Query("DELETE FROM recipes")
    suspend fun deleteAllRecipes()

    @Upsert
    suspend fun upsertRecipe(recipe: RecipeEntity)

    @Upsert
    suspend fun upsertAll(recipes: List<RecipeEntity>)

    @Query("SELECT * FROM recipes WHERE syncState = 'PENDING'")
    suspend fun getDirty(): List<RecipeEntity>

    /**
     * Get paginated recipes that are either:
     * 1. Public (visible to everyone)
     * 2. Created by the specified user (regardless of privacy)
     *
     * Sort order:
     * 1. User's recipes (public + private) sorted by updatedAt DESC
     * 2. Other users' public recipes sorted by updatedAt DESC
     *
     * This creates a "My Recipes" + "Explore" experience.
     *
     * @param userId The user ID to check for creator ownership
     * @param limit Maximum number of results to return
     * @param offset Number of results to skip (for pagination)
     *  @return List of recipes with their details
     */
    @Transaction
    @Query(
        """
        SELECT r.* 
        FROM recipes r
        WHERE r.deletedAt IS NULL
            AND (
                r.privacy = 'PUBLIC'
                OR r.creatorId = :userId
            )
        ORDER BY 
            CASE WHEN r.creatorId = :userId THEN 0 ELSE 1 END,
            r.updatedAt DESC
        LIMIT :limit OFFSET :offset
    """
    )
    suspend fun getRecipesWithDetailsForUserPaginated(
        userId: UUID,
        limit: Int,
        offset: Int
    ): List<RecipeWithDetails>

}
