package com.tenmilelabs.chefai.core.data.timer

/**
 * Snapshot of the single, app-wide recipe step timer. `null` in [RecipeTimerController.state]
 * means no timer is running or paused.
 */
data class RecipeTimerState(
    val stepLabel: String,
    val totalSeconds: Long,
    val remainingSeconds: Long,
    val isRunning: Boolean,
) {
    val isFinished: Boolean get() = remainingSeconds <= 0
}
