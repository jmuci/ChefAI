package com.tenmilelabs.chefai.recipes.domain.usecase

import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDraftDao
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraftEntity
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Persists a scraped [RecipeDraft] so the editor can pick it up by id after navigation.
 *
 * Shared by both import entry points — the URL screen and the browser fallback — which must hand
 * off to the editor identically. Also where the draft's hero image is downloaded and cached
 * on-device (see [CacheRecipeImage]) — the editor loads the draft once on init rather than
 * observing it, so the image has to be in place *before* the draft is written, not raced in
 * afterward.
 */
class SaveImportedDraft @Inject constructor(
    private val recipeDraftDao: RecipeDraftDao,
    private val cacheRecipeImage: CacheRecipeImage,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(draft: RecipeDraft) = withContext(ioDispatcher) {
        val localImagePath = cacheRecipeImage(draft.recipeId, draft.imageUrl)
        recipeDraftDao.saveDraft(draft.copy(localImagePath = localImagePath).toRecipeDraftEntity())
    }
}
