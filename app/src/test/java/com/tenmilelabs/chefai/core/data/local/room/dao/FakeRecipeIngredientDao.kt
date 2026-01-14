package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

class FakeRecipeIngredientDao : RecipeIngredientDao {

    private val recipeIngredientsFlow = MutableStateFlow<List<RecipeIngredientEntity>>(emptyList())

    override suspend fun upsertRecipeIngredient(recipeIngredient: RecipeIngredientEntity) {
        val currentList = recipeIngredientsFlow.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.recipeId == recipeIngredient.recipeId && it.ingredientId == recipeIngredient.ingredientId }
        if (existingIndex != -1) {
            currentList[existingIndex] = recipeIngredient
        } else {
            currentList.add(recipeIngredient)
        }
        recipeIngredientsFlow.value = currentList
    }

    override suspend fun upsertAll(recipeIngredients: List<RecipeIngredientEntity>) {
        val currentList = recipeIngredientsFlow.value.toMutableList()
        recipeIngredients.forEach { recipeIngredient ->
            val existingIndex = currentList.indexOfFirst { it.recipeId == recipeIngredient.recipeId && it.ingredientId == recipeIngredient.ingredientId }
            if (existingIndex != -1) {
                currentList[existingIndex] = recipeIngredient
            } else {
                currentList.add(recipeIngredient)
            }
        }
        recipeIngredientsFlow.value = currentList
    }

    override suspend fun deleteRecipeIngredient(recipeId: UUID, ingredientId: UUID) {
        recipeIngredientsFlow.value = recipeIngredientsFlow.value.filterNot { it.recipeId == recipeId && it.ingredientId == ingredientId }
    }

    override suspend fun deleteAllForRecipe(recipeId: UUID) {
        recipeIngredientsFlow.value = recipeIngredientsFlow.value.filterNot { it.recipeId == recipeId }
    }

    override suspend fun getDirty(): List<RecipeIngredientEntity> {
        return recipeIngredientsFlow.value.filter { it.syncState == SyncState.PENDING }
    }
}
