package com.tenmilelabs.chefai.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.util.Async
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * UiState for the home screen.
 */
data class HomeUiState(
    val recipes: List<RecipePreview> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * One-time events for the home screen.
 */
sealed interface HomeUiEvent {
    data class ShowSnackbar(val message: Int) : HomeUiEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    recipesRepository: RecipesRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _uiEvent = Channel<HomeUiEvent>()
    val uiEvents = _uiEvent.receiveAsFlow()

    private val _recipesAsync = recipesRepository.getRecipesPreviewStream()
        .map { Async.Success(it) }
        .catch<Async<List<RecipePreview>>> { e ->
            if (e is CancellationException) throw e
            emit(Async.Error(R.string.loading_recipes_error))
        }

    val uiState: StateFlow<HomeUiState> = combine(_isLoading, _recipesAsync)
    { isLoading, recipesAsync ->
        when (recipesAsync) {
            Async.Loading -> {
                HomeUiState(isLoading = true)
            }
            is Async.Error -> {
                viewModelScope.launch {
                    _uiEvent.send(HomeUiEvent.ShowSnackbar(recipesAsync.errorMessage))
                }
                HomeUiState(isLoading = false)
            }
            is Async.Success -> {
                HomeUiState(
                    recipes = recipesAsync.data,
                    isLoading = isLoading
                )
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = HomeUiState(isLoading = true)
        )
}
