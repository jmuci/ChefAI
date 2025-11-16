package com.tenmilelabs.chefai.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.domain.model.RecipePreview
import com.tenmilelabs.chefai.domain.repository.RecipesRepository
import com.tenmilelabs.chefai.util.Async
import com.tenmilelabs.chefai.util.WhileUiSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.CancellationException
import javax.inject.Inject


/**
 * UiState for the task list screen.
 */
data class RecipesUiState(
    val items: List<RecipePreview> = emptyList(),
    val isLoading: Boolean = false,
    val userMessage: Int? = null,
)

@HiltViewModel
class RecipesViewModel @Inject constructor(
    recipesRepository: RecipesRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _userMessage: MutableStateFlow<Int?> = MutableStateFlow(null)
    private val _recipesAsync = recipesRepository.getRecipesPreviewStream()
        .map { Async.Success(it) }
        .catch<Async<List<RecipePreview>>> { e ->
            if (e is CancellationException) throw e
            emit(Async.Error(R.string.loading_recipes_error))
        }

    val uiState: StateFlow<RecipesUiState> = combine(_isLoading, _recipesAsync, _userMessage)
        { isLoading, recipesAsync, userMessage ->
            when (recipesAsync) {
                Async.Loading -> {
                    RecipesUiState(isLoading = true)
                }
                is Async.Error -> {
                    RecipesUiState(userMessage = recipesAsync.errorMessage)
                }
                is Async.Success -> {
                    RecipesUiState(
                        items = recipesAsync.data,
                        isLoading = isLoading,
                        userMessage = R.string.loading_recipes_success)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = RecipesUiState(isLoading = true)
        )

    fun snackbarMessageShown() {
        _userMessage.value = null
    }

}