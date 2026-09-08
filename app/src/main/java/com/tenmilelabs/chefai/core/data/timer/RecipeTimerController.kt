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
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

    /**
     * Overridable in tests so the countdown can be driven by a virtual clock — the same escape
     * hatch [com.tenmilelabs.chefai.auth.domain.SessionManager.uuidGenerator] uses, and for the
     * same reason: Hilt has no binding for it and it needs none.
     */
    internal var timeSource: TimeSource = TimeSource.Monotonic

    private val _state = MutableStateFlow<RecipeTimerState?>(null)
    val state: StateFlow<RecipeTimerState?> = _state.asStateFlow()

    private var tickJob: Job? = null

    /**
     * When the running timer is due, or null while paused or stopped.
     *
     * The countdown reads from this rather than subtracting one from [RecipeTimerState] per tick.
     * A tick fires *at least* a second after the last one, never exactly one — the delay is
     * scheduled after the previous tick's work, and the main thread it runs on is shared with
     * recomposition — so decrementing accumulated every one of those overruns as lost time. Worse,
     * a process the platform freezes while it is in the background (the common case for a cooking
     * timer: start it, put the phone down) stops ticking entirely, and on resuming the old code
     * carried on counting from a value minutes out of date. Reading a deadline instead means the
     * first tick after any stall reports the truth, and fires immediately if the time has passed.
     *
     * Still not fixed by this, and not fixable in the process: a device in deep sleep advances
     * neither the ticks nor `TimeSource.Monotonic`, so the completion notification is late by
     * however long the device slept. Only an `AlarmManager` alarm delivers that on time.
     */
    private var deadline: TimeMark? = null

    /**
     * Starts a countdown, returning whatever timer it replaced (already running or paused), or
     * null if there wasn't one — so the caller can tell the user what got cancelled.
     */
    fun start(stepLabel: String, totalSeconds: Long): RecipeTimerState? {
        if (totalSeconds <= 0) return null
        val replaced = _state.value
        tickJob?.cancel()
        deadline = timeSource.markNow() + totalSeconds.seconds
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
        deadline = null
        _state.update { it?.copy(isRunning = false) }
    }

    fun resume() {
        val current = _state.value ?: return
        if (current.isRunning || current.isFinished) return
        deadline = timeSource.markNow() + current.remainingSeconds.seconds
        _state.update { it?.copy(isRunning = true) }
        tickJob = applicationScope.launch(Dispatchers.Main.immediate) { runCountdown() }
    }

    fun cancel() {
        tickJob?.cancel()
        tickJob = null
        deadline = null
        _state.value = null
    }

    private suspend fun runCountdown() {
        while (true) {
            delay(1_000)
            val current = _state.value ?: return
            val due = deadline ?: return
            val remaining = remainingSecondsUntil(due)
            if (remaining <= 0) {
                deadline = null
                _state.update { it?.copy(remainingSeconds = 0, isRunning = false) }
                notifier.notifyTimerComplete(current.stepLabel)
                return
            }
            _state.update { it?.copy(remainingSeconds = remaining) }
        }
    }

    /**
     * Whole seconds left before [due], rounded up so a timer started at 30 reads "30" until the
     * first full second has actually gone by rather than dropping to 29 immediately.
     */
    private fun remainingSecondsUntil(due: TimeMark): Long =
        ceil(-due.elapsedNow().inWholeMilliseconds / 1000.0).toLong()
}
