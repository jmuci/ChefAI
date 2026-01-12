package com.tenmilelabs.chefai.home.ui

import androidx.lifecycle.ViewModel
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/**
 * UiState for the home screen.
 */
data class HomeUiState(
    val forYouRecipes: List<RecipePreview> = PreviewData.forYouPreview,
    val lowCarbRecipes: List<RecipePreview> = PreviewData.lowCarbsPreviewList,
    val italianRecipes: List<RecipePreview> = PreviewData.italianPreviewList,
    val dessertRecipes: List<RecipePreview> = PreviewData.dessertsPreviewList,
    val isLoading: Boolean = false
)

/**
 * One-time events for the home screen.
 */
sealed interface HomeUiEvent {
    data class ShowSnackbar(val message: Int) : HomeUiEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    // Using Channel for one-time events instead of SharedFlow
    private val _uiEvent = Channel<HomeUiEvent>()
    val uiEvents = _uiEvent.receiveAsFlow()

    val uiState: HomeUiState = HomeUiState()

    fun snackbarMessageShown() {
        // Channel events are automatically consumed, no need for cleanup
    }

    // TODO: Add methods to load recipes from repository when implemented
    // fun refreshRecipes() { ... }
}