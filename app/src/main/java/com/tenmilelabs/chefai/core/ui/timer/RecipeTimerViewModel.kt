package com.tenmilelabs.chefai.core.ui.timer

import androidx.lifecycle.ViewModel
import com.tenmilelabs.chefai.core.data.timer.RecipeTimerController
import com.tenmilelabs.chefai.core.data.timer.RecipeTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin Compose-facing adapter over the app-wide [RecipeTimerController]. Kept as a `@HiltViewModel`
 * (rather than injecting the controller straight into composables) to match this codebase's
 * existing pattern for singleton-backed UI state (see `SyncStatusViewModel`) — the underlying state
 * lives in the controller singleton, so it's shared correctly no matter which
 * `ViewModelStoreOwner` this instance is scoped to.
 */
@HiltViewModel
class RecipeTimerViewModel @Inject constructor(
    private val controller: RecipeTimerController,
) : ViewModel() {

    val state: StateFlow<RecipeTimerState?> = controller.state

    fun start(stepLabel: String, totalSeconds: Long) = controller.start(stepLabel, totalSeconds)
    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun cancel() = controller.cancel()
}
