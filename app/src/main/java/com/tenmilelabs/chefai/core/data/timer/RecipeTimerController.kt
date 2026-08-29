package com.tenmilelabs.chefai.core.data.timer

import com.tenmilelabs.chefai.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide holder for the single active recipe step timer. Runs on [applicationScope] rather than
 * a screen's `viewModelScope`, so the countdown survives navigating away from the recipe that
 * started it — it only stops when the process dies or [cancel] is called. Starting a new timer
 * replaces whatever was running; this app doesn't support more than one concurrent timer yet.
 */
@Singleton
class RecipeTimerController @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val notifier: RecipeTimerNotifier,
) {

    private val _state = MutableStateFlow<RecipeTimerState?>(null)
    val state: StateFlow<RecipeTimerState?> = _state.asStateFlow()

    private var tickJob: Job? = null

    fun start(stepLabel: String, totalSeconds: Long) {
        if (totalSeconds <= 0) return
        tickJob?.cancel()
        _state.value = RecipeTimerState(
            stepLabel = stepLabel,
            totalSeconds = totalSeconds,
            remainingSeconds = totalSeconds,
            isRunning = true,
        )
        tickJob = applicationScope.launch { runCountdown() }
    }

    fun pause() {
        val current = _state.value ?: return
        if (!current.isRunning) return
        tickJob?.cancel()
        _state.value = current.copy(isRunning = false)
    }

    fun resume() {
        val current = _state.value ?: return
        if (current.isRunning || current.isFinished) return
        _state.value = current.copy(isRunning = true)
        tickJob = applicationScope.launch { runCountdown() }
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
                _state.value = current.copy(remainingSeconds = 0, isRunning = false)
                notifier.notifyTimerComplete(current.stepLabel)
                return
            }
            _state.value = current.copy(remainingSeconds = remaining)
        }
    }
}
