package com.tenmilelabs.chefai.core.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import java.util.UUID

/**
 * Data Access Object for the recipe_ingredients table.
 */
@Dao
interface RecipeIngredientDao {
    @Upsert
    suspend fun upsertRecipeIngredient(recipeIngredient: RecipeIngredientEntity)

    @Upsert
    suspend fun upsertAll(recipeIngredients: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId AND ingredientId = :ingredientId")
    suspend fun deleteRecipeIngredient(recipeId: UUID, ingredientId: UUID)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteAllForRecipe(recipeId: UUID)

    @Query("SELECT * FROM recipe_ingredients WHERE syncState = 'PENDING'")
    suspend fun getDirty(): List<RecipeIngredientEntity>

    /**
     * Upserts all ingredient cross-references for a recipe.
     * This is a transactional operation that replaces all existing ingredients for the recipe.
     */
    @Transaction
    suspend fun upsertAllForRecipe(
        recipeId: UUID,
        recipeIngredients: List<RecipeIngredientEntity>
    ) {
        deleteAllForRecipe(recipeId)
        upsertAll(recipeIngredients)
    }
}
