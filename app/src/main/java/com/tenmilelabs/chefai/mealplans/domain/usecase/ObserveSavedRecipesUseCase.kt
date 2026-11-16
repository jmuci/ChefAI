package com.tenmilelabs.chefai.mealplans.domain.usecase

import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The current user's saved recipes (bookmarks), by title — what a meal-plan recipe picker offers.
 * The same set the Recipes tab shows, never the public catalog. Shared by the week view's
 * "+ Add meal" sheet and the plan screens' "Add recipe" gaps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSavedRecipesUseCase @Inject constructor(
    private val sessionManager: SessionManager,
    private val collectionsRepository: CollectionsRepository,
    private val recipesRepository: RecipesRepository,
) {
    operator fun invoke(): Flow<List<RecipePreview>> = sessionManager.userSession
        .flatMapLatest { session ->
            when (session) {
                is UserSession.Loading -> emptyFlow()
                is UserSession.Anonymous -> collectionsRepository.observeBookmarkedRecipeIds(session.localUserId)
                is UserSession.Authenticated -> collectionsRepository.observeBookmarkedRecipeIds(session.user.uuid)
            }
        }
        .flatMapLatest { ids -> recipesRepository.getRecipePreviewsByIds(ids.toList()) }
        .map { recipes -> recipes.sortedBy { it.title.lowercase() } }
}
