package com.tenmilelabs.chefai.recipes.ui.urlimport.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult
import com.tenmilelabs.chefai.recipes.domain.repository.RecipeImporter
import com.tenmilelabs.chefai.recipes.domain.usecase.SaveImportedDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import javax.inject.Inject

/**
 * Drives the visible browser fallback: the screen owns the WebView and feeds DOM snapshots in here,
 * where each is run through the ordinary import path until one yields a recipe.
 *
 * Snapshots are cheap to reject — the parser bails immediately on a page with no schema.org markup
 * — so this deliberately does no "is the challenge done yet?" detection. It just keeps looking.
 */
@HiltViewModel
class BrowserImportViewModel @Inject constructor(
    private val recipeImporter: RecipeImporter,
    private val saveImportedDraft: SaveImportedDraft,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val url: String = savedStateHandle.get<String>(AppDestinationArgs.IMPORT_URL_ARG)
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        .orEmpty()

    private val _state = MutableStateFlow(BrowserImportState(url = url))
    val state: StateFlow<BrowserImportState> = _state.asStateFlow()

    private val _effects = Channel<BrowserImportEffect>(Channel.BUFFERED)
    val effects: Flow<BrowserImportEffect> = _effects.receiveAsFlow()

    fun dispatch(action: BrowserImportAction) {
        when (action) {
            is BrowserImportAction.SnapshotCaptured -> handleSnapshot(action.html)

            BrowserImportAction.SettleTimeoutElapsed -> _state.update {
                it.copy(phase = BrowserImportState.Phase.AWAITING_USER)
            }

            BrowserImportAction.GiveUp -> _state.update {
                it.copy(errorRes = R.string.import_recipe_browser_gave_up)
            }

            BrowserImportAction.EnterManually -> viewModelScope.launch {
                _effects.send(BrowserImportEffect.NavigateToManualEditor)
            }

            BrowserImportAction.Cancel -> viewModelScope.launch {
                _effects.send(BrowserImportEffect.NavigateBack)
            }
        }
    }

    private fun handleSnapshot(html: String) {
        // The polling loop can land another snapshot while the previous one is still being mapped;
        // parsing them concurrently would race two drafts to the editor.
        if (!_state.value.isPolling) return
        _state.update { it.copy(isExtracting = true) }

        viewModelScope.launch {
            when (val result = recipeImporter.importFromHtml(html, url)) {
                is RecipeImportResult.Success -> {
                    saveImportedDraft(result.draft)
                    _effects.send(BrowserImportEffect.NavigateToEditorWithDraft(result.draft.recipeId))
                }

                // Not there yet — the challenge is still up, or the page is mid-render. Keep
                // polling rather than treating this as a failure.
                else -> {
                    Timber.d("Browser import snapshot had no recipe yet: %s", result)
                    _state.update { it.copy(isExtracting = false) }
                }
            }
        }
    }
}
