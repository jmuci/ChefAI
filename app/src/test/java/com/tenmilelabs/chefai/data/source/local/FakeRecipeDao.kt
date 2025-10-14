package com.tenmilelabs.chefai.data.source.local

import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.local.room.UserEntity
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithDetails
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithLabels
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithTags
import com.tenmilelabs.chefai.data.source.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * A fake implementation of [RecipeDao] for testing purposes.
 *
 * This class simulates the behavior of the real DAO, including transactional queries
 * that would normally be handled by Room.
 */
class FakeRecipeDao(
    initialRecipes: List<RecipeEntity> = emptyList(),
    initialUsers: List<UserEntity> = emptyList()
) : RecipeDao {

    private var _recipes: MutableMap<UUID, RecipeEntity> = mutableMapOf()
    private var _users: MutableMap<UUID, UserEntity> = mutableMapOf()

    var recipes: List<RecipeEntity>?
        get() = _recipes?.values?.toList()
        set(newRecipes) {
            _recipes.clear()
            newRecipes?.associateByTo(_recipes) { it.uuid }
        }

    init {
        _recipes = initialRecipes.associateBy { it.uuid }.toMutableMap()
        _users = initialUsers.associateBy { it.uuid }.toMutableMap()
    }

    override suspend fun getAllRecipes(): List<RecipeEntity> =
        _recipes.values.toList()

    override fun observeAll(): Flow<List<RecipeEntity>> {
        return flowOf(_recipes.values.toList())
    }

    override suspend fun getRecipeById(uuid: UUID): RecipeEntity? = _recipes[uuid]
    override fun observeRecipeById(uuid: UUID): Flow<RecipeEntity?> {
        return flowOf(_recipes[uuid])
    }

    override suspend fun getRecipeWithDetails(uuid: UUID): RecipeWithDetails? {
        val recipe = _recipes[uuid] ?: return null
        return recipe
    }

    override fun observeRecipeWithDetails(uuid: UUID): Flow<RecipeWithDetails?> {
        return observeRecipeById(uuid).map { recipe ->
            recipe?.let {
                val user = _users[it.creatorId]
                    ?: throw IllegalStateException("User not found for recipe creator")
                RecipeWithDetails(
                    recipe = it,
                    creator = user,
                    steps = emptyList(),
                    ingredients = emptyList(),
                    tags = emptyList(),
                    labels = emptyList()
                )
            }
        }
    }

    override fun observeRecipesWithDetails(): Flow<List<RecipeWithDetails>> {
        return observeAll().map { recipeList ->
            recipeList.map { recipe ->
                val user = _users[recipe.creatorId]
                    ?: throw IllegalStateException("User not found for recipe creator: ${recipe.creatorId}")
                // This is a simplification; a real implementation would join other tables.
                RecipeWithDetails(
                    recipe = recipe,
                    creator = user,
                    steps = emptyList(),
                    ingredients = emptyList(),
                    tags = emptyList(),
                    labels = emptyList()
                )
            }
        }
    }

    override suspend fun getRecipeWithTags(uuid: UUID): RecipeWithTags? {
        val recipe = _recipes[uuid] ?: return null
        return RecipeWithTags(recipe, emptyList())
    }

    override fun observeRecipesWithTags(): Flow<List<RecipeWithTags>> {
        return flowOf(_recipes.values.map { RecipeWithTags(it, emptyList()) })
    }

    override suspend fun getRecipeWithLabels(uuid: UUID): RecipeWithLabels? {
        val recipe = _recipes[uuid] ?: return null
        return RecipeWithLabels(recipe, emptyList())
    }

    override fun observeRecipesWithLabels(): Flow<List<RecipeWithLabels>> {
        return flowOf(_recipes.values.map { RecipeWithLabels(it, emptyList()) })
    }

    override suspend fun deleteRecipe(uuid: UUID) {
        _recipes.remove(uuid)
    }

    override suspend fun deleteAllRecipes() {
        _recipes.clear()
    }

    override suspend fun upsertRecipe(recipe: RecipeEntity) {
        _recipes[recipe.uuid] = recipe
    }

    override suspend fun upsertAll(recipes: List<RecipeEntity>) {
        recipes.forEach { upsertRecipe(it) }
    }

    override suspend fun getDirty(): List<RecipeEntity> {
        return _recipes.values.filter { it.syncState == SyncState.PENDING }
    }
}