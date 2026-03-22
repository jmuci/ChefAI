package com.tenmilelabs.chefai.recipes.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.collections.domain.repository.CollectionsRepository
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.util.Async
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.recipes.domain.repository.RecipesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RecipesDetailsUiState(
    val recipe: Recipe? = null,
    val isLoading: Boolean = false,
    val isBookmarked: Boolean = false,
    val userMessage: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeDetailsViewModel @Inject constructor(
    recipesRepository: RecipesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val sessionManager: SessionManager,
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

    private val _isBookmarked: Flow<Boolean> = sessionManager.userSession
        .flatMapLatest { session ->
            val userId = when (session) {
                is UserSession.Loading -> return@flatMapLatest flowOf(false)
                is UserSession.Anonymous -> session.localUserId
                is UserSession.Authenticated -> session.user.uuid
            }
            collectionsRepository.observeBookmarkedRecipeIds(userId).map { ids -> recipeUuid in ids }
        }

    val uiState: StateFlow<RecipesDetailsUiState> = combine(
        _isLoading, _recipeAsync, _userMessage, _isBookmarked
    ) { isLoading, recipeAsync, userMessage, isBookmarked ->
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
                    isBookmarked = isBookmarked,
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

    fun toggleBookmark() {
        val userId = sessionManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            if (uiState.value.isBookmarked) {
                collectionsRepository.removeBookmark(userId, recipeUuid)
            } else {
                collectionsRepository.addBookmark(userId, recipeUuid)
            }
        }
    }
}