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
 * off to the editor identically.
 */
class SaveImportedDraft @Inject constructor(
    private val recipeDraftDao: RecipeDraftDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(draft: RecipeDraft) = withContext(ioDispatcher) {
        recipeDraftDao.saveDraft(draft.toRecipeDraftEntity())
    }
}
