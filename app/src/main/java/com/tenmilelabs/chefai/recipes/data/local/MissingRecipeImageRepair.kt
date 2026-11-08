package com.tenmilelabs.chefai.recipes.data.local

import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDao
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeImageStateDao
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Clears `localImagePath` on every recipe whose cached image file is gone, so the UI falls back to
 * the remote URL instead of showing a broken image.
 *
 * `recipeImageModel` always prefers the on-device copy, and Coil has no second source to try when
 * that `file://` fails, so a row pointing at a missing file renders the error placeholder even when
 * its `imageUrl` loads fine. The usual way that happens is Android Auto Backup: the Room database is
 * backed up but `recipe_images/` is excluded (see `backup_rules.xml`), so a reinstall or a new phone
 * restores rows whose files never came back. `RecipeImageBackfillWorker` already repairs this, but
 * only on a charger and unmetered network — hours away for someone opening the app for the first
 * time.
 *
 * Each repaired row also gets a fresh set of download attempts, and a backfill is scheduled, so the
 * image is cached again once conditions allow. Cheap enough to run on every app start: one
 * query and a `stat` per cached image.
 */
class MissingRecipeImageRepair @Inject constructor(
    private val recipeDao: RecipeDao,
    private val recipeImageStateDao: RecipeImageStateDao,
    private val syncScheduler: SyncScheduler,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Test seam: whether a cached image file is present. */
    internal var fileExists: (String) -> Boolean = { File(it).exists() }

    /** @return how many recipes had their missing image path cleared. */
    suspend operator fun invoke(): Int = withContext(ioDispatcher) {
        val missing = recipeDao.getLocalImagePaths().filterNot { fileExists(it.localImagePath) }
        if (missing.isEmpty()) return@withContext 0

        missing.forEach { entry ->
            recipeDao.clearLocalImagePathIfUnchanged(entry.recipeId, entry.localImagePath)
            recipeImageStateDao.delete(entry.recipeId)
        }
        Timber.i("Cleared %d recipe image path(s) pointing at missing files", missing.size)
        syncScheduler.scheduleImageBackfill()
        missing.size
    }
}
