package com.tenmilelabs.chefai.search.ui

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import com.tenmilelabs.chefai.search.domain.repository.RecipeSearchOutcome
import com.tenmilelabs.chefai.search.domain.repository.RecipeSearchRepository
import com.tenmilelabs.chefai.search.domain.repository.RecipeSearchSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MILLIS = 300L
private const val MIN_QUERY_LENGTH = 3

/** UI state for the Home search overlay. */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    data class Results(
        val items: List<RecipePreview>,
        val bookmarkedRecipeIds: Set<UUID> = emptySet(),
        /** True when [items] came from the on-device fallback, not the server — see [RecipeSearchSource]. */
        val isOffline: Boolean,
    ) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val messageRes: Int) : SearchUiState
}

/** One-time events for the Home search overlay. */
sealed interface SearchUiEvent {
    data class ShowSnackbar(val messageRes: Int) : SearchUiEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeSearchViewModel @Inject constructor(
    private val recipeSearchRepository: RecipeSearchRepository,
    private val recipesRepository: RecipesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val sessionManager: SessionManager,
    private val syncScheduler: SyncScheduler,
    private val imageLoader: ImageLoader,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiEvent = MutableSharedFlow<SearchUiEvent>(replay = 1)
    val uiEvents: SharedFlow<SearchUiEvent> = _uiEvent.asSharedFlow()

    /**
     * One-shot, not replayed (unlike [uiEvents]) — a stale navigation event replaying into a
     * freshly recomposed collector would silently re-navigate the user to a recipe they already
     * backed out of.
     */
    private val _navigateToRecipe = Channel<UUID>(Channel.BUFFERED)
    val navigateToRecipe: Flow<UUID> = _navigateToRecipe.receiveAsFlow()

    private val _bookmarkedIds: Flow<Set<UUID>> = sessionManager.userSession
        .flatMapLatest { session ->
            when (session) {
                is UserSession.Loading -> flowOf(emptySet())
                is UserSession.Anonymous -> collectionsRepository.observeBookmarkedRecipeIds(session.localUserId)
                is UserSession.Authenticated -> collectionsRepository.observeBookmarkedRecipeIds(session.user.uuid)
            }
        }

    private val _searchResult: Flow<SearchUiState> = _query
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest<_, SearchUiState> { trimmed ->
            if (trimmed.length < MIN_QUERY_LENGTH) {
                flowOf(SearchUiState.Idle)
            } else {
                flow {
                    emit(SearchUiState.Searching)
                    emit(runSearch(trimmed))
                }
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            Timber.e(e, "Search failed")
            emit(SearchUiState.Error(R.string.search_error))
        }

    val uiState: StateFlow<SearchUiState> = combine(_searchResult, _bookmarkedIds) { result, bookmarkedIds ->
        if (result is SearchUiState.Results) result.copy(bookmarkedRecipeIds = bookmarkedIds) else result
    }.stateIn(
        scope = viewModelScope,
        started = WhileUiSubscribed,
        initialValue = SearchUiState.Idle,
    )

    private suspend fun runSearch(query: String): SearchUiState {
        val outcome: RecipeSearchOutcome = recipeSearchRepository.search(query)
        prefetchImages(outcome.results)
        return if (outcome.results.isEmpty()) {
            SearchUiState.Empty
        } else {
            SearchUiState.Results(
                items = outcome.results,
                isOffline = outcome.source == RecipeSearchSource.LOCAL_FALLBACK,
            )
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    /**
     * [RecipeDetailsScreen] reads only from Room, but a search result may be a recipe this device
     * hasn't pulled yet (see [onSaveToCollection]'s doc for why that's real, not just theoretical).
     * Navigating straight there would land on the "recipe not found" screen, so gate on local
     * existence first: if it's missing, kick a sync and tell the user instead of opening a dead
     * end — mirroring the same check [onSaveToCollection] already does for bookmarking.
     */
    fun onRecipeClick(recipeId: UUID) {
        viewModelScope.launch {
            if (recipesRepository.getRecipeStream(recipeId).first() != null) {
                _navigateToRecipe.send(recipeId)
            } else {
                syncScheduler.requestImmediateSync()
                _uiEvent.emit(SearchUiEvent.ShowSnackbar(R.string.search_recipe_not_yet_synced))
            }
        }
    }

    /**
     * Sync pull already ships every PUBLIC recipe to every device, so this is rare — only a
     * recipe made public since the device's last pull is missing locally — but search is what
     * makes a not-yet-synced recipe's UUID reachable at all, which nothing else in the app could
     * do before. [BookmarkedRecipeEntity] enforces its FK to [RecipeEntity] at runtime (Room emits
     * `PRAGMA foreign_keys = ON`), so this must be real, not just documented intent.
     */
    fun onSaveToCollection(recipeId: UUID) {
        val userId = when (val session = sessionManager.userSession.value) {
            is UserSession.Anonymous -> session.localUserId
            is UserSession.Authenticated -> session.user.uuid
            is UserSession.Loading -> return
        }
        viewModelScope.launch {
            if (recipesRepository.getRecipeStream(recipeId).first() == null) {
                syncScheduler.requestImmediateSync()
                _uiEvent.emit(SearchUiEvent.ShowSnackbar(R.string.search_recipe_not_yet_synced))
                return@launch
            }
            try {
                collectionsRepository.addBookmark(userId, recipeId)
                _uiEvent.emit(SearchUiEvent.ShowSnackbar(R.string.search_added_to_collection))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLiteConstraintException) {
                Timber.w(e, "Bookmark failed for $recipeId — recipe not yet synced")
                _uiEvent.emit(SearchUiEvent.ShowSnackbar(R.string.search_recipe_not_yet_synced))
            }
        }
    }

    /** Enqueues Coil prefetch requests so thumbnails are warm in the cache when cards render. */
    private fun prefetchImages(recipes: List<RecipePreview>) {
        recipes.forEach { recipe ->
            val model = recipeImageModel(recipe.localImagePath, recipe.imageUrlThumbnail) ?: return@forEach
            imageLoader.enqueue(ImageRequest.Builder(appContext).data(model).build())
        }
    }
}
