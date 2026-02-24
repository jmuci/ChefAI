package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeRecipeStepDao : RecipeStepDao {

    private val stepsFlow = MutableStateFlow<List<RecipeStepEntity>>(emptyList())

    override suspend fun getAllRecipeSteps(): List<RecipeStepEntity> {
        return stepsFlow.value
    }

    override fun observeAll(): Flow<List<RecipeStepEntity>> {
        return stepsFlow
    }

    override suspend fun getRecipeStepById(uuid: UUID): RecipeStepEntity? {
        return stepsFlow.value.find { it.uuid == uuid }
    }

    override fun observeRecipeStepById(uuid: UUID): Flow<RecipeStepEntity?> {
        return stepsFlow.map { steps -> steps.find { it.uuid == uuid } }
    }

    override suspend fun deleteRecipeStep(uuid: UUID) {
        stepsFlow.value = stepsFlow.value.filterNot { it.uuid == uuid }
    }

    override suspend fun deleteAllRecipeSteps() {
        stepsFlow.value = emptyList()
    }

    override suspend fun upsertStep(step: RecipeStepEntity) {
        val currentSteps = stepsFlow.value.toMutableList()
        val existingIndex = currentSteps.indexOfFirst { it.uuid == step.uuid }
        if (existingIndex != -1) {
            currentSteps[existingIndex] = step
        } else {
            currentSteps.add(step)
        }
        stepsFlow.value = currentSteps
    }

    override suspend fun markPendingForRecipes(recipeIds: List<UUID>, updatedAt: Long) {
        stepsFlow.value = stepsFlow.value.map { step ->
            if (step.recipeId in recipeIds) {
                step.copy(syncState = SyncState.PENDING, updatedAt = updatedAt)
            } else {
                step
            }
        }
    }
}
