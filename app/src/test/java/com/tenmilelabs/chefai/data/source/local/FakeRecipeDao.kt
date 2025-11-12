package com.tenmilelabs.chefai.data.source.local

import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf

class FakeRecipeDao(initialRecipes: List<RecipeEntity> = emptyList()) : RecipeDao {
    private var _recipes: MutableMap<String, RecipeEntity>? = null
    var recipes: List<RecipeEntity>?
        get() = _recipes?.values?.toList()
        set(newRecipes) {
            _recipes = newRecipes?.associateBy { it.uuid }?.toMutableMap()
        }

    init {
        recipes = initialRecipes
    }

    override suspend fun getAllRecipes(): List<RecipeEntity> =
        recipes ?: throw Exception("Recipes list is null")

    override fun observeAll(): Flow<List<RecipeEntity>> {
        return flowOf(recipes ?: throw Exception("Recipes list is null"))
    }

    override fun getRecipeById(uuid: String): RecipeEntity? = _recipes?.get(uuid)

    override fun observeRecipeById(uuid: String): Flow<RecipeEntity> {
        return flowOf(_recipes?.get(uuid)).filterNotNull()
    }

    override suspend fun deleteRecipe(uuid: String) {
        _recipes?.remove(uuid)
    }

    override suspend fun deleteAllRecipes() {
        _recipes?.clear()
    }

    override suspend fun upsertRecipe(recipe: RecipeEntity) {
        _recipes?.put(recipe.uuid, recipe)
    }

    override fun upsertAll(recipes: List<RecipeEntity>) {
        _recipes?.putAll(recipes.associateBy { it.uuid })
    }
}