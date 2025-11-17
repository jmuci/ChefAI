package com.tenmilelabs.chefai.data.source.local

import com.tenmilelabs.chefai.data.source.local.room.IngredientEntity
import com.tenmilelabs.chefai.data.source.local.room.LabelEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeIngredientCrossRef
import com.tenmilelabs.chefai.data.source.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.data.source.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.data.source.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.data.source.local.room.TagEntity
import com.tenmilelabs.chefai.data.source.local.room.UserEntity
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithDetails
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithLabels
import com.tenmilelabs.chefai.data.source.local.room.relations.RecipeWithTags
import com.tenmilelabs.chefai.data.source.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * A fake implementation of [RecipeDao] for testing. It simulates a relational database
 * in memory, allowing for testing of repository logic that depends on transactions and relations.
 */
class FakeRecipeDao : RecipeDao {

    // In-memory storage for all entities and relations
    private val users = mutableMapOf<UUID, UserEntity>()
    private val recipes = mutableMapOf<UUID, RecipeEntity>()
    private val ingredients = mutableMapOf<UUID, IngredientEntity>()
    private val labels = mutableMapOf<UUID, LabelEntity>()
    private val tags = mutableMapOf<UUID, TagEntity>()
    private val steps = mutableMapOf<UUID, RecipeStepEntity>()
    private val recipeIngredients = mutableListOf<RecipeIngredientCrossRef>()
    private val recipeLabels = mutableListOf<RecipeLabelCrossRef>()
    private val recipeTags = mutableListOf<RecipeTagCrossRef>()

    // A trigger to notify observers of data changes
    private val trigger = MutableStateFlow(0)

    fun seed(
        users: List<UserEntity> = emptyList(),
        recipes: List<RecipeEntity> = emptyList(),
        ingredients: List<IngredientEntity> = emptyList(),
        labels: List<LabelEntity> = emptyList(),
        tags: List<TagEntity> = emptyList(),
        steps: List<RecipeStepEntity> = emptyList(),
        recipeIngredients: List<RecipeIngredientCrossRef> = emptyList(),
        recipeLabels: List<RecipeLabelCrossRef> = emptyList(),
        recipeTags: List<RecipeTagCrossRef> = emptyList()
    ) {
        users.associateByTo(this.users) { it.uuid }
        recipes.associateByTo(this.recipes) { it.uuid }
        ingredients.associateByTo(this.ingredients) { it.uuid }
        labels.associateByTo(this.labels) { it.uuid }
        tags.associateByTo(this.tags) { it.uuid }
        steps.associateByTo(this.steps) { it.uuid }
        this.recipeIngredients.addAll(recipeIngredients)
        this.recipeLabels.addAll(recipeLabels)
        this.recipeTags.addAll(recipeTags)
        triggerUpdate()
    }

    private fun triggerUpdate() {
        trigger.value++
    }

    private fun assembleRecipeWithDetails(recipe: RecipeEntity): RecipeWithDetails {
        val creator = users[recipe.creatorId]
            ?: throw IllegalStateException("User with id ${recipe.creatorId} not found for recipe ${recipe.uuid}")

        val recipeSteps = steps.values.filter { it.recipeId == recipe.uuid }.sortedBy { it.orderIndex }
        
        val ingredientIds = recipeIngredients.filter { it.recipeId == recipe.uuid }.map { it.ingredientId }
        val recipeIngredientsList = ingredients.values.filter { it.uuid in ingredientIds }

        val tagIds = recipeTags.filter { it.recipeId == recipe.uuid }.map { it.tagId }
        val recipeTagsList = tags.values.filter { it.uuid in tagIds }

        val labelIds = recipeLabels.filter { it.recipeId == recipe.uuid }.map { it.labelId }
        val recipeLabelsList = labels.values.filter { it.uuid in labelIds }

        return RecipeWithDetails(
            recipe = recipe,
            creator = creator,
            steps = recipeSteps,
            ingredients = recipeIngredientsList,
            tags = recipeTagsList,
            labels = recipeLabelsList
        )
    }

    override fun observeAllRecipesForUser(creatorId: UUID): Flow<List<RecipeEntity>> {
        return trigger.map { recipes.values.filter { it.creatorId == creatorId } }
    }

    override suspend fun getRecipeById(uuid: UUID): RecipeEntity? = recipes[uuid]

    override fun observeRecipeById(uuid: UUID): Flow<RecipeEntity?> = trigger.map { recipes[uuid] }

    override suspend fun getRecipeWithDetails(uuid: UUID): RecipeWithDetails? {
        return recipes[uuid]?.let { assembleRecipeWithDetails(it) }
    }

    override fun observeRecipeWithDetails(uuid: UUID): Flow<RecipeWithDetails?> {
        return trigger.map { recipes[uuid]?.let { assembleRecipeWithDetails(it) } }
    }

    override fun observeRecipesWithDetails(): Flow<List<RecipeWithDetails>> {
        return trigger.map { recipes.values.map { assembleRecipeWithDetails(it) } }
    }

    override fun observeRecipesWithDetailsForUser(creatorId: UUID): Flow<List<RecipeWithDetails>> {
        return trigger.map { recipes.values.filter { it.creatorId == creatorId }.map { assembleRecipeWithDetails(it) } }
    }

    override suspend fun getRecipeWithTags(uuid: UUID): RecipeWithTags? {
        val recipe = recipes[uuid] ?: return null
        val tagIds = recipeTags.filter { it.recipeId == uuid }.map { it.tagId }
        return RecipeWithTags(recipe, tags.values.filter { it.uuid in tagIds })
    }

    override fun observeRecipesWithTags(): Flow<List<RecipeWithTags>> {
        return trigger.map {
            recipes.values.map { recipe ->
                val tagIds = recipeTags.filter { it.recipeId == recipe.uuid }.map { it.tagId }
                RecipeWithTags(recipe, tags.values.filter { it.uuid in tagIds })
            }
        }
    }

    override suspend fun getRecipeWithLabels(uuid: UUID): RecipeWithLabels? {
        val recipe = recipes[uuid] ?: return null
        val labelIds = recipeLabels.filter { it.recipeId == uuid }.map { it.labelId }
        return RecipeWithLabels(recipe, labels.values.filter { it.uuid in labelIds })
    }

    override fun observeRecipesWithLabels(): Flow<List<RecipeWithLabels>> {
        return trigger.map {
            recipes.values.map { recipe ->
                val labelIds = recipeLabels.filter { it.recipeId == recipe.uuid }.map { it.labelId }
                RecipeWithLabels(recipe, labels.values.filter { it.uuid in labelIds })
            }
        }
    }

    override suspend fun deleteRecipe(uuid: UUID) {
        recipes.remove(uuid)
        steps.values.removeAll { it.recipeId == uuid }
        recipeIngredients.removeAll { it.recipeId == uuid }
        recipeLabels.removeAll { it.recipeId == uuid }
        recipeTags.removeAll { it.recipeId == uuid }
        triggerUpdate()
    }

    override suspend fun deleteAllRecipes() {
        recipes.clear()
        steps.clear()
        recipeIngredients.clear()
        recipeLabels.clear()
        recipeTags.clear()
        triggerUpdate()
    }

    override suspend fun upsertRecipe(recipe: RecipeEntity) {
        recipes[recipe.uuid] = recipe
        triggerUpdate()
    }

    override suspend fun upsertAll(recipes: List<RecipeEntity>) {
        recipes.forEach { this.recipes[it.uuid] = it }
        triggerUpdate()
    }

    override suspend fun getDirty(): List<RecipeEntity> {
        return recipes.values.filter { it.syncState == SyncState.PENDING }
    }
}
