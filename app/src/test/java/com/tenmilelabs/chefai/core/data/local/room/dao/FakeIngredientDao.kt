package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.relations.IngredientWithDetails
import com.tenmilelabs.chefai.core.data.local.room.relations.IngredientWithRecipes
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeIngredientDao : IngredientDao {

    private val ingredientsFlow = MutableStateFlow<List<IngredientEntity>>(emptyList())

    override suspend fun getAllIngredients(): List<IngredientEntity> {
        return ingredientsFlow.value
    }

    override fun observeAll(): Flow<List<IngredientEntity>> {
        return ingredientsFlow
    }

    override suspend fun getIngredientById(uuid: UUID): IngredientEntity? {
        return ingredientsFlow.value.find { it.uuid == uuid }
    }

    override fun observeIngredientById(uuid: UUID): Flow<IngredientEntity?> {
        return ingredientsFlow.map { ingredients -> ingredients.find { it.uuid == uuid } }
    }

    override suspend fun getIngredientWithRecipes(uuid: UUID): IngredientWithRecipes? {
        // Not implemented for fake
        return null
    }

    override fun observeIngredientsWithRecipes(): Flow<List<IngredientWithRecipes>> {
        // Not implemented for fake
        return MutableStateFlow(emptyList())
    }

    override suspend fun getIngredientWithDetails(uuid: UUID): IngredientWithDetails? {
        // Not implemented for fake
        return null
    }

    override fun observeIngredientsWithDetails(): Flow<List<IngredientWithDetails>> {
        // Not implemented for fake
        return MutableStateFlow(emptyList())
    }

    override suspend fun deleteIngredient(uuid: UUID) {
        ingredientsFlow.value = ingredientsFlow.value.filterNot { it.uuid == uuid }
    }

    override suspend fun deleteAllIngredients() {
        ingredientsFlow.value = emptyList()
    }

    override suspend fun upsertIngredient(ingredient: IngredientEntity) {
        val currentList = ingredientsFlow.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.uuid == ingredient.uuid }
        if (existingIndex != -1) {
            currentList[existingIndex] = ingredient
        } else {
            currentList.add(ingredient)
        }
        ingredientsFlow.value = currentList
    }

    override suspend fun upsertAll(ingredients: List<IngredientEntity>) {
        val currentList = ingredientsFlow.value.toMutableList()
        ingredients.forEach { ingredient ->
            val existingIndex = currentList.indexOfFirst { it.uuid == ingredient.uuid }
            if (existingIndex != -1) {
                currentList[existingIndex] = ingredient
            } else {
                currentList.add(ingredient)
            }
        }
        ingredientsFlow.value = currentList
    }

    override suspend fun getDirty(): List<IngredientEntity> {
        return ingredientsFlow.value.filter { it.syncState == SyncState.PENDING }
    }

    override suspend fun getExistingIds(uuids: List<UUID>): List<UUID> {
        return ingredientsFlow.value.filter { uuids.contains(it.uuid) }.map { it.uuid }
    }

    override suspend fun getSyncedExistingIds(uuids: List<UUID>): List<UUID> {
        return ingredientsFlow.value
            .filter { uuids.contains(it.uuid) && it.syncState == SyncState.SYNCED }
            .map { it.uuid }
    }
}
