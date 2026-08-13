package com.tenmilelabs.chefai.recipes.domain.usecase

import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.recipes.data.local.RecipeImageStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Copies a photo the user picked from their device into app-internal storage, keyed by recipe id.
 *
 * The sibling of [CacheRecipeImage] for the other way an image enters a recipe. Where that one
 * downloads a scraped hero image past a CDN's bot wall, this one takes ownership of a local file the
 * picker handed us: [android.content.ContentResolver] grants from
 * [androidx.activity.result.contract.ActivityResultContracts.GetContent] are transient and die with
 * the Activity, so a `content://` string that is merely *remembered* is unreadable after process
 * death. Copying the bytes at pick time is what makes the choice durable.
 *
 * Writes to the same path the saved recipe will read from — [com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft.recipeId]
 * exists from the moment a draft does — so nothing has to be moved when the user taps Save.
 *
 * Never fails the caller: any error returns `null`, leaving the recipe imageless rather than
 * blocking the edit.
 */
class CachePickedImage @Inject constructor(
    private val recipeImageStore: RecipeImageStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(recipeId: UUID, uri: String): String? = withContext(ioDispatcher) {
        if (uri.isBlank()) return@withContext null

        recipeImageStore.writeFromUri(recipeId, uri).also { storedPath ->
            if (storedPath == null) Timber.w("Could not store picked image for %s", recipeId)
        }
    }
}
