package com.tenmilelabs.chefai.core.data.timer

import com.tenmilelabs.chefai.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide holder for the single active recipe step timer. Uses [applicationScope] rather than a
 * screen's `viewModelScope`, so the countdown survives navigating away from the recipe that
 * started it — it only stops when the process dies or [cancel] is called. Starting a new timer
 * replaces whatever was running; this app doesn't support more than one concurrent timer yet.
 *
 * Every mutating call ([start], [pause], [resume], [cancel]) is invoked directly from a Compose
 * click handler, i.e. already on the main thread — so the tick loop is deliberately launched on
 * [Dispatchers.Main] rather than [applicationScope]'s own (background) dispatcher. That confines
 * every read of and write to [_state] to a single thread, so `tickJob?.cancel()` in [start]/
 * [cancel] is guaranteed to take effect before the cancelled job's next line runs — nothing can
 * interleave on a single-threaded dispatcher. Using the background dispatcher here instead would
 * make the tick loop and the click-handler calls genuine concurrent writers of [_state], racing on
 * a plain read-modify-write with no lock — e.g. a `pause()` landing between a tick's read and its
 * write could get silently clobbered back to "running" by that tick, leaving the timer stuck with
 * no job actually counting down.
 */
@Singleton
class RecipeTimerController @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val notifier: RecipeTimerNotifier,
) {

    private val _state = MutableStateFlow<RecipeTimerState?>(null)
    val state: StateFlow<RecipeTimerState?> = _state.asStateFlow()

    private var tickJob: Job? = null

    /**
     * Starts a countdown, returning whatever timer it replaced (already running or paused), or
     * null if there wasn't one — so the caller can tell the user what got cancelled.
     */
    fun start(stepLabel: String, totalSeconds: Long): RecipeTimerState? {
        if (totalSeconds <= 0) return null
        val replaced = _state.value
        tickJob?.cancel()
        _state.value = RecipeTimerState(
            stepLabel = stepLabel,
            totalSeconds = totalSeconds,
            remainingSeconds = totalSeconds,
            isRunning = true,
        )
        tickJob = applicationScope.launch(Dispatchers.Main.immediate) { runCountdown() }
        return replaced
    }

    fun pause() {
        val current = _state.value ?: return
        if (!current.isRunning) return
        tickJob?.cancel()
        _state.update { it?.copy(isRunning = false) }
    }

    fun resume() {
        val current = _state.value ?: return
        if (current.isRunning || current.isFinished) return
        _state.update { it?.copy(isRunning = true) }
        tickJob = applicationScope.launch(Dispatchers.Main.immediate) { runCountdown() }
    }

    fun cancel() {
        tickJob?.cancel()
        tickJob = null
        _state.value = null
    }

    private suspend fun runCountdown() {
        while (true) {
            delay(1_000)
            val current = _state.value ?: return
            val remaining = current.remainingSeconds - 1
            if (remaining <= 0) {
                _state.update { it?.copy(remainingSeconds = 0, isRunning = false) }
                notifier.notifyTimerComplete(current.stepLabel)
                return
            }
            _state.update { it?.copy(remainingSeconds = remaining) }
        }
    }
}
