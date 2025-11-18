package com.tenmilelabs.chefai.data.source.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithDetails
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithLabels
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithTags
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for the recipes table.
 */
@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes WHERE creatorId = :creatorId")
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

}
