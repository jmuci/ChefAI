package com.tenmilelabs.chefai.home.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.collections.domain.model.RecipeBookmark
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.domain.repository.HomeLayoutRepository
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

/** UI state for the SDUI home screen. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val components: List<ComponentModel>,
        /** Recipe data keyed by UUID string, resolved from Room for each card in [components]. */
        val recipes: Map<String, RecipePreview>,
        /** The user's bookmarks keyed by recipe id; a recipe missing from it isn't bookmarked. */
        val bookmarks: Map<UUID, RecipeBookmark> = emptyMap(),
        ) : HomeUiState
    data class Error(val message: Int) : HomeUiState
}

/** One-time events emitted by the home screen ViewModel. */
sealed interface HomeUiEvent {
    data class ShowSnackbar(val message: Int) : HomeUiEvent
    data class NavigateToRecipeDetail(val recipeUuid: UUID) : HomeUiEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    homeLayoutRepository: HomeLayoutRepository,
    private val recipesRepository: RecipesRepository,
    private val imageLoader: ImageLoader,
    private val collectionsRepository: CollectionsRepository,
    private val sessionManager: SessionManager,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiEvent = Channel<HomeUiEvent>()
    val uiEvents = _uiEvent.receiveAsFlow()

    // Follows the session rather than reading the user id once: on a cold start the session is
    // still Loading when Home is built, and it changes again on login/logout.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _bookmarks = sessionManager.userSession.flatMapLatest { session ->
        when (session) {
            is UserSession.Loading -> flowOf(emptyMap())
            is UserSession.Anonymous -> collectionsRepository.observeBookmarks(session.localUserId)
            is UserSession.Authenticated -> collectionsRepository.observeBookmarks(session.user.uuid)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = homeLayoutRepository.getHomeLayout()
        .flatMapLatest<_, HomeUiState> { layout ->
            val filtered = layout.components.filterNot { it is ComponentModel.Unknown }
            val recipeIds = extractRecipeIds(filtered)
            if (recipeIds.isEmpty()) {
                _bookmarks.map { bookmarks ->
                    HomeUiState.Success(
                        components = filtered,
                        recipes = emptyMap(),
                        bookmarks = bookmarks,
                    )
                }
            } else {
                val recipesFlow = recipesRepository.getRecipePreviewsByIds(recipeIds)
                    .map { list ->
                        prefetchImages(list)
                        list.associateBy { it.uuid.toString() }
                    }
                combine(recipesFlow, _bookmarks) { recipesMap, bookmarks ->
                    HomeUiState.Success(
                        components = filtered,
                        recipes = recipesMap,
                        bookmarks = bookmarks,
                    ) as HomeUiState
                }
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to load home layout")
            viewModelScope.launch {
                _uiEvent.send(HomeUiEvent.ShowSnackbar(R.string.loading_recipes_error))
            }
            emit(HomeUiState.Error(R.string.loading_recipes_error))
        }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = HomeUiState.Loading,
        )

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.CardClicked -> {
                val uuid = runCatching { UUID.fromString(action.recipeId) }.getOrNull()
                if (uuid != null) {
                    viewModelScope.launch {
                        _uiEvent.send(HomeUiEvent.NavigateToRecipeDetail(uuid))
                    }
                } else {
                    Timber.w("CardClicked with invalid or null recipeId: '${action.recipeId}'")
                }
            }
            is HomeAction.SectionActionClicked -> {
                // TODO: Handle deep-link / section action navigation
                Timber.d("Section action clicked: ${action.actionUrl}")
            }
            is HomeAction.BookmarkUpdated -> {
                val userId = sessionManager.getCurrentUserId() ?: return
                viewModelScope.launch {
                    collectionsRepository.update(userId, action.recipeId, action.update)
                }
            }
        }
    }

    /**
     * Extracts all distinct recipe UUIDs from a flat list of components,
     * including items nested inside [ComponentModel.Carousel]s.
     */
    private fun extractRecipeIds(components: List<ComponentModel>): List<UUID> {
        return buildList {
            components.forEach { component ->
                when (component) {
                    is ComponentModel.Carousel -> component.items.forEach { item ->
                        recipeUuidFrom(item)?.let { add(it) }
                    }
                    else -> recipeUuidFrom(component)?.let { add(it) }
                }
            }
        }.distinct()
    }

    private fun recipeUuidFrom(component: ComponentModel): UUID? {
        val idStr = when (component) {
            is ComponentModel.LargeCard -> component.recipeId
            is ComponentModel.SquaredCard -> component.recipeId
            is ComponentModel.ListCard -> component.recipeId
            else -> null
        } ?: return null
        return runCatching { UUID.fromString(idStr) }.getOrElse {
            Timber.w("Invalid recipeId in component '${component.id}': $idStr")
            null
        }
    }

    /**
     * Enqueues Coil prefetch requests for resolved recipe image URLs so they are
     * warm in the cache when composables render them.
     */
    private fun prefetchImages(recipes: List<RecipePreview>) {
        recipes.forEach { recipe ->
            val model = recipeImageModel(recipe.localImagePath, recipe.imageUrlThumbnail) ?: return@forEach
            imageLoader.enqueue(ImageRequest.Builder(appContext).data(model).build())
        }
    }
}
