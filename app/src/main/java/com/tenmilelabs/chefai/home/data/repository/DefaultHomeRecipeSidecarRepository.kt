package com.tenmilelabs.chefai.home.data.repository

import com.tenmilelabs.chefai.core.data.local.room.IngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeIngredientEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeStepEntity
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.IngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.LabelDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeIngredientDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeStepDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.TagDao
import com.tenmilelabs.chefai.core.data.local.room.dao.UserDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.home.data.mapper.toIngredientEntity
import com.tenmilelabs.chefai.home.data.mapper.toLabelEntity
import com.tenmilelabs.chefai.home.data.mapper.toRecipeEntity
import com.tenmilelabs.chefai.home.data.mapper.toRecipeIngredientEntity
import com.tenmilelabs.chefai.home.data.mapper.toRecipeStepEntity
import com.tenmilelabs.chefai.home.data.mapper.toTagEntity
import com.tenmilelabs.chefai.home.data.mapper.toUserEntity
import com.tenmilelabs.chefai.home.data.model.HomeSidecarDto
import com.tenmilelabs.chefai.home.domain.repository.HomeRecipeSidecarRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

class DefaultHomeRecipeSidecarRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val tagDao: TagDao,
    private val labelDao: LabelDao,
    private val userDao: UserDao,
    private val recipeTagCrossRefDao: RecipeTagCrossRefDao,
    private val recipeLabelCrossRefDao: RecipeLabelCrossRefDao,
    private val ingredientDao: IngredientDao,
    private val recipeIngredientDao: RecipeIngredientDao,
    private val recipeStepDao: RecipeStepDao,
    private val transactionRunner: TransactionRunner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HomeRecipeSidecarRepository {

    override suspend fun upsertSidecar(sidecar: HomeSidecarDto): Int = withContext(ioDispatcher) {
        transactionRunner {
            // 1. Upsert reference data first (FK order: creators → tags → labels), unconditionally —
            //    this must happen even if every recipe below ends up skipped, otherwise a sidecar
            //    that only carries new tags/labels for existing recipes would silently drop them.
            userDao.upsertAll(sidecar.creators.map { it.toUserEntity() })
            tagDao.upsertAll(sidecar.tags.map { it.toTagEntity() })
            labelDao.upsertAll(sidecar.labels.map { it.toLabelEntity() })

            // 2. Determine which recipes to skip: PENDING rows (unpushed local edits) and any recipe
            //    that already has detail (steps or ingredients) — i.e. one that arrived complete via
            //    sync, fetch, or an earlier sidecar. The sidecar synthesizes its own step/ingredient
            //    ids, so writing over such a recipe would duplicate every step and ingredient. Only
            //    detail-less rows are (re)written, so a later delivery can still fill them in.
            val recipeUuids = sidecar.recipes.mapNotNull { runCatching { UUID.fromString(it.uuid) }.getOrNull() }
            val existingById = recipeUuids.mapNotNull { recipeDao.getRecipeById(it) }.associateBy { it.uuid }
            val skipIds = existingById.values.filter { existing ->
                existing.syncState == SyncState.PENDING ||
                    recipeStepDao.getStepsForRecipe(existing.uuid).isNotEmpty() ||
                    recipeIngredientDao.getIngredientsForRecipe(existing.uuid).isNotEmpty()
            }.mapTo(mutableSetOf()) { it.uuid }

            val recipesToWrite = sidecar.recipes.filter { dto ->
                val uuid = runCatching { UUID.fromString(dto.uuid) }.getOrNull() ?: return@filter false
                uuid !in skipIds
            }

            if (recipesToWrite.isEmpty()) {
                Timber.d("Sidecar: all ${sidecar.recipes.size} recipes already exist — skipping write")
                return@transactionRunner 0
            }

            // 3. Upsert recipes, keeping device-local columns the sidecar knows nothing about.
            val recipeEntities = recipesToWrite.map { dto ->
                val entity = dto.toRecipeEntity()
                existingById[entity.uuid]?.let { existing ->
                    entity.copy(
                        localImagePath = existing.localImagePath,
                        imageBlobId = existing.imageBlobId,
                    )
                } ?: entity
            }
            recipeDao.upsertAll(recipeEntities)

            // 4. Upsert cross-refs for written recipes only
            val writtenIds = recipeEntities.map { it.uuid }.toSet()
            val now = System.currentTimeMillis()

            val tagCrossRefs = recipesToWrite
                .filter { UUID.fromString(it.uuid) in writtenIds }
                .flatMap { dto ->
                    val recipeId = UUID.fromString(dto.uuid)
                    dto.tagIds.mapNotNull { tagIdStr ->
                        runCatching { UUID.fromString(tagIdStr) }.getOrNull()?.let { tagId ->
                            RecipeTagCrossRef(
                                recipeId = recipeId,
                                tagId = tagId,
                                updatedAt = now,
                                deletedAt = null,
                                syncState = SyncState.SYNCED,
                            )
                        }
                    }
                }

            val labelCrossRefs = recipesToWrite
                .filter { UUID.fromString(it.uuid) in writtenIds }
                .flatMap { dto ->
                    val recipeId = UUID.fromString(dto.uuid)
                    dto.labelIds.mapNotNull { labelIdStr ->
                        runCatching { UUID.fromString(labelIdStr) }.getOrNull()?.let { labelId ->
                            RecipeLabelCrossRef(
                                recipeId = recipeId,
                                labelId = labelId,
                                updatedAt = now,
                                deletedAt = null,
                                syncState = SyncState.SYNCED,
                            )
                        }
                    }
                }

            if (tagCrossRefs.isNotEmpty()) recipeTagCrossRefDao.upsertAll(tagCrossRefs)
            if (labelCrossRefs.isNotEmpty()) recipeLabelCrossRefDao.upsertAll(labelCrossRefs)

            // 5. Upsert ingredients and steps for written recipes
            val allIngredients = mutableListOf<IngredientEntity>()
            val allRecipeIngredients = mutableListOf<RecipeIngredientEntity>()
            val allSteps = mutableListOf<RecipeStepEntity>()

            for (dto in recipesToWrite) {
                val recipeId = runCatching { UUID.fromString(dto.uuid) }.getOrNull() ?: continue
                dto.ingredients.forEach { sidecarIngredient ->
                    allIngredients.add(sidecarIngredient.toIngredientEntity(now))
                    allRecipeIngredients.add(sidecarIngredient.toRecipeIngredientEntity(recipeId, now))
                }
                dto.steps.forEach { sidecarStep ->
                    allSteps.add(sidecarStep.toRecipeStepEntity(recipeId, now))
                }
            }

            if (allIngredients.isNotEmpty()) ingredientDao.upsertAll(allIngredients)
            if (allRecipeIngredients.isNotEmpty()) recipeIngredientDao.upsertAll(allRecipeIngredients)
            if (allSteps.isNotEmpty()) recipeStepDao.upsertAll(allSteps)

            Timber.d("Sidecar: wrote ${recipesToWrite.size} recipes (skipped ${skipIds.size})")
            recipesToWrite.size
        }
    }
}
