package com.tenmilelabs.chefai.data.source.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tenmilelabs.chefai.data.source.local.room.RecipeIngredientCrossRef
import java.util.UUID

/**
 * Data Access Object for the recipe_ingredients table.
 */
@Dao
interface RecipeIngredientDao {
    @Upsert
    suspend fun upsertRecipeIngredient(recipeIngredient: RecipeIngredientCrossRef)

    @Upsert
    suspend fun upsertAll(recipeIngredients: List<RecipeIngredientCrossRef>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId AND ingredientId = :ingredientId")
    suspend fun deleteRecipeIngredient(recipeId: UUID, ingredientId: UUID)

    @Query("SELECT * FROM recipe_ingredients WHERE syncState = 'PENDING'")
    suspend fun getDirty(): List<RecipeIngredientCrossRef>
}
