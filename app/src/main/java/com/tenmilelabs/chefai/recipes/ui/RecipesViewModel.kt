package com.tenmilelabs.chefai.recipes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
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
    recipesRepository: RecipesRepository,
    sessionManager: SessionManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _uiEvent = MutableSharedFlow<RecipesUiEvent>(replay = 1)
    val uiEvents: SharedFlow<RecipesUiEvent> = _uiEvent.asSharedFlow()

    private val _recipesAsync = (sessionManager.getCurrentUserId()?.let { userId ->
        recipesRepository.getRecipesPreviewStreamForUser(userId)
    } ?: emptyFlow())
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

}