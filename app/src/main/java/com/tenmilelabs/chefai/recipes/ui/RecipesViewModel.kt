package com.tenmilelabs.chefai.recipes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.data.sync.SyncScheduler
import com.tenmilelabs.chefai.core.data.sync.SyncStatus
import com.tenmilelabs.chefai.core.data.sync.SyncStatusHolder
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.util.Async
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject


/**
 * UiState for the recipes screen.
 */
data class RecipesUiState(
    val items: List<RecipePreview> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)

/**
 * One-time events for the recipes screen.
 */
sealed interface RecipesUiEvent {
    data class ShowSnackbar(val message: Int) : RecipesUiEvent
}

@HiltViewModel
class RecipesViewModel @Inject constructor(
    recipesRepository: RecipesRepository,
    sessionManager: SessionManager,
    private val syncScheduler: SyncScheduler,
    private val syncStatusHolder: SyncStatusHolder,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _uiEvent = MutableSharedFlow<RecipesUiEvent>(replay = 1)
    val uiEvents: SharedFlow<RecipesUiEvent> = _uiEvent.asSharedFlow()

    private val _recipesAsync = (sessionManager.getCurrentUserId()?.let { userId ->
        // TODO when user logs out, we should stop showing the last users recipes.
        recipesRepository.getRecipesPreviewStreamForUser(userId)
    } ?: emptyFlow())
        .map { Async.Success(it) }
        .catch<Async<List<RecipePreview>>> { e ->
            if (e is CancellationException) throw e
            emit(Async.Error(R.string.loading_recipes_error))
        }

    val uiState: StateFlow<RecipesUiState> = combine(
        _isLoading, _recipesAsync, syncStatusHolder.syncStatus
    ) { isLoading, recipesAsync, syncStatus ->
        val isRefreshing = syncStatus is SyncStatus.Syncing
        when (recipesAsync) {
            Async.Loading -> {
                RecipesUiState(isLoading = true, isRefreshing = isRefreshing)
            }
            is Async.Error -> {
                // Emit error event asynchronously
                viewModelScope.launch {
                    _uiEvent.emit(RecipesUiEvent.ShowSnackbar(recipesAsync.errorMessage))
                }
                RecipesUiState(isLoading = false, isRefreshing = isRefreshing)
            }
            is Async.Success -> {
                RecipesUiState(
                    items = recipesAsync.data,
                    isLoading = isLoading,
                    isRefreshing = isRefreshing,
                )
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = RecipesUiState(isLoading = true)
        )

    fun onRefresh() {
        syncStatusHolder.emitStatus(SyncStatus.Syncing)
        syncScheduler.requestManualSync()
    }

}