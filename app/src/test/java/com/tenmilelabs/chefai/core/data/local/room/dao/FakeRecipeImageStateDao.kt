package com.tenmilelabs.chefai.core.data.local.room.dao

import com.tenmilelabs.chefai.core.data.local.room.RecipeImageStateEntity
import java.util.UUID

/**
 * In-memory [RecipeImageStateDao].
 *
 * The real DAO's methods are hand-written SQL upserts rather than `@Upsert`, precisely so the
 * download and upload counters can be touched independently; this fake mirrors that field-by-field
 * rather than replacing whole rows, or it would hide the very bug that shape exists to prevent.
 */
class FakeRecipeImageStateDao : RecipeImageStateDao {

    private val rows = mutableMapOf<UUID, RecipeImageStateEntity>()

    private fun rowFor(recipeId: UUID) = rows[recipeId] ?: RecipeImageStateEntity(
        recipeId = recipeId,
        attempts = 0,
        lastAttemptAt = 0,
    )

    override suspend fun upsert(state: RecipeImageStateEntity) {
        rows[state.recipeId] = state
    }

    override suspend fun getByRecipeId(recipeId: UUID): RecipeImageStateEntity? = rows[recipeId]

    override suspend fun delete(recipeId: UUID) {
        rows.remove(recipeId)
    }

    override suspend fun recordDownloadAttempt(recipeId: UUID, at: Long) {
        val row = rowFor(recipeId)
        rows[recipeId] = row.copy(attempts = row.attempts + 1, lastAttemptAt = at)
    }

    override suspend fun recordUploadAttempt(recipeId: UUID, at: Long) {
        val row = rowFor(recipeId)
        rows[recipeId] = row.copy(uploadAttempts = row.uploadAttempts + 1, lastUploadAttemptAt = at)
    }

    override suspend fun recordUploadSuccess(recipeId: UUID, modifiedAt: Long?) {
        val row = rowFor(recipeId)
        rows[recipeId] = row.copy(uploadAttempts = 0, uploadedFileModifiedAt = modifiedAt)
    }

    override suspend fun burnUploadAttempts(recipeId: UUID, at: Long, cap: Int) {
        val row = rowFor(recipeId)
        rows[recipeId] = row.copy(uploadAttempts = cap, lastUploadAttemptAt = at)
    }
}
