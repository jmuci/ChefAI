package com.tenmilelabs.chefai.recipes.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.util.Async
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

/**
 * UiState for the task list screen.
 */
data class RecipesDetailsUiState(
    val recipe: Recipe? = null,
    val isLoading: Boolean = false,
    val userMessage: Int? = null,
)


@HiltViewModel
class RecipeDetailsViewModel @Inject constructor(
    recipesRepository: RecipesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val recipeUuid: UUID = UUID.fromString(savedStateHandle[AppDestinationArgs.RECIPE_ID_ARG]!!)

    private val _isLoading = MutableStateFlow(false)
    private val _userMessage: MutableStateFlow<Int?> = MutableStateFlow(null)
    private val _recipeAsync: Flow<Async<Recipe>> = recipesRepository.getRecipeStream(recipeUuid)
        .map {
            if (it != null) {
                Async.Success(it)
            } else {
                Async.Error(R.string.loading_recipe_details_error)
            }
        }
        .catch { emit(Async.Error(R.string.loading_recipe_details_error)) }

    val uiState: StateFlow<RecipesDetailsUiState> = combine(_isLoading, _recipeAsync, _userMessage)
    { isLoading, recipeAsync, userMessage ->
        when (recipeAsync) {
            Async.Loading -> {
                RecipesDetailsUiState(isLoading = true)
            }

            is Async.Error -> {
                RecipesDetailsUiState(userMessage = recipeAsync.errorMessage)
            }

            is Async.Success -> {
                RecipesDetailsUiState(
                    recipe = recipeAsync.data,
                    isLoading = isLoading,
                )
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = RecipesDetailsUiState(isLoading = true)
        )

    fun snackbarMessageShown() {
        _userMessage.value = null
    }
}