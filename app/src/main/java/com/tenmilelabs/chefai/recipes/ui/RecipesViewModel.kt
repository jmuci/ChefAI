package com.tenmilelabs.chefai.recipes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.util.Async
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    val isLoading: Boolean = false
)

/**
 * One-time events for the recipes screen.
 */
sealed interface RecipesUiEvent {
    data class ShowSnackbar(val message: Int) : RecipesUiEvent
}

@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val recipesRepository: RecipesRepository
) : ViewModel() {

    // TODO: Replace with actual user ID from SessionManager/AuthRepository
    private val testUserId = java.util.UUID.fromString("F47AC10B-58CC-4372-A567-0E02B2C3D479")

    private val _isLoading = MutableStateFlow(false)
    private val _uiEvent = MutableSharedFlow<RecipesUiEvent>(replay = 0)
    val uiEvents: SharedFlow<RecipesUiEvent> = _uiEvent.asSharedFlow()

    /**
     * Paging flow for recipes.
     * Use cachedIn(viewModelScope) to ensure the PagingData is cached and survives
     * configuration changes.
     */
    val recipesPagingFlow: Flow<PagingData<RecipePreview>> =
        recipesRepository.getRecipesPreviewPager(
            userId = testUserId,
            pageSize = 10       // TODO Change to 30
        ).cachedIn(viewModelScope)

    /**
     * Expose load state for UI to observe and handle errors
     */
    fun onLoadError() {
        viewModelScope.launch {
            _uiEvent.emit(RecipesUiEvent.ShowSnackbar(R.string.loading_recipes_error))
        }
    }

    // Keep the old non-paged flow for backward compatibility during migration
    // You can remove this once you've fully migrated to paging in the UI
    private val _recipesAsync = recipesRepository.getRecipesPreviewStream()
        .map { Async.Success(it) }
        .catch<Async<List<RecipePreview>>> { e ->
            if (e is CancellationException) throw e
            emit(Async.Error(R.string.loading_recipes_error))
        }

    val uiState: StateFlow<RecipesUiState> = combine(_isLoading, _recipesAsync)
    { isLoading, recipesAsync ->
        when (recipesAsync) {
            Async.Loading -> {
                RecipesUiState(isLoading = true)
            }
            is Async.Error -> {
                // Emit error event asynchronously
                viewModelScope.launch {
                    _uiEvent.emit(RecipesUiEvent.ShowSnackbar(recipesAsync.errorMessage))
                }
                RecipesUiState(isLoading = false)
            }
            is Async.Success -> {
                RecipesUiState(
                    items = recipesAsync.data,
                    isLoading = isLoading
                )
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = RecipesUiState(isLoading = true)
        )

    /**
     * Trigger a refresh of the paging data.
     * Call this when you need to refresh the list (e.g., pull-to-refresh).
     */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            // Paging 3 will automatically refresh when you call refresh() on LazyPagingItems
            // in the UI layer
            _isLoading.value = false
        }
    }
}