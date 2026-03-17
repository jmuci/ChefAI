package com.tenmilelabs.chefai.home.data.repository

import com.tenmilelabs.chefai.core.data.local.room.RecipeLabelCrossRef
import com.tenmilelabs.chefai.core.data.local.room.RecipeTagCrossRef
import com.tenmilelabs.chefai.core.data.local.room.TransactionRunner
import com.tenmilelabs.chefai.core.data.local.room.dao.LabelDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeLabelCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeTagCrossRefDao
import com.tenmilelabs.chefai.core.data.local.room.dao.TagDao
import com.tenmilelabs.chefai.core.data.local.room.dao.UserDao
import com.tenmilelabs.chefai.core.data.local.util.SyncState
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.home.data.mapper.toLabelEntity
import com.tenmilelabs.chefai.home.data.mapper.toRecipeEntity
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
    private val transactionRunner: TransactionRunner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HomeRecipeSidecarRepository {

    override suspend fun upsertSidecar(sidecar: HomeSidecarDto): Int = withContext(ioDispatcher) {
        transactionRunner {
            // 1. Determine which recipes to skip (already PENDING or SYNCED — don't overwrite user edits or server-owned data)
            val recipeUuids = sidecar.recipes.mapNotNull { runCatching { UUID.fromString(it.uuid) }.getOrNull() }
            val skipIds = recipeUuids.filter { uuid ->
                val existing = recipeDao.getRecipeById(uuid)
                existing != null && (existing.syncState == SyncState.PENDING || existing.syncState == SyncState.SYNCED)
            }.toSet()

            val recipesToWrite = sidecar.recipes.filter { dto ->
                val uuid = runCatching { UUID.fromString(dto.uuid) }.getOrNull() ?: return@filter false
                uuid !in skipIds
            }

            if (recipesToWrite.isEmpty()) {
                Timber.d("Sidecar: all ${sidecar.recipes.size} recipes already exist — skipping write")
                return@transactionRunner 0
            }

            // 2. Upsert in FK order: creators → tags → labels → recipes → cross-refs
            userDao.upsertAll(sidecar.creators.map { it.toUserEntity() })
            tagDao.upsertAll(sidecar.tags.map { it.toTagEntity() })
            labelDao.upsertAll(sidecar.labels.map { it.toLabelEntity() })

            val recipeEntities = recipesToWrite.map { it.toRecipeEntity() }
            recipeDao.upsertAll(recipeEntities)

            // 3. Upsert cross-refs for written recipes only
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

            Timber.d("Sidecar: wrote ${recipesToWrite.size} recipes (skipped ${skipIds.size})")
            recipesToWrite.size
        }
    }
}
