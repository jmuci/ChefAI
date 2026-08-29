package com.tenmilelabs.chefai.core.ui.timer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import kotlin.math.roundToInt

private val WIDGET_SIZE = 88.dp
private val EDGE_PADDING = 16.dp

/**
 * App-wide floating widget for the active [com.tenmilelabs.chefai.core.data.timer.RecipeTimerState].
 * Renders nothing when no timer is running or paused. Placed once, above the nav graph (see
 * `MainActivity`), so it survives navigating between screens — dragging is local to this
 * composition and resets to the default corner on process death, which is an accepted limitation
 * for now (see the recipe-step-timer PR notes).
 */
@Composable
fun FloatingRecipeTimerWidget(
    modifier: Modifier = Modifier,
    viewModel: RecipeTimerViewModel = hiltViewModel(),
) {
    val timerState by viewModel.state.collectAsStateWithLifecycle()
    val current = timerState ?: return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val widgetSizePx = with(density) { WIDGET_SIZE.toPx() }
        val edgePaddingPx = with(density) { EDGE_PADDING.toPx() }

        var offsetX by remember { mutableFloatStateOf(maxWidthPx - widgetSizePx - edgePaddingPx) }
        var offsetY by remember { mutableFloatStateOf(maxHeightPx * 0.6f) }

        Card(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(WIDGET_SIZE)
                .testTag("RecipeTimerWidget")
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxWidthPx - widgetSizePx)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxHeightPx - widgetSizePx)
                    }
                },
            shape = CircleShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = formatTimerDuration(current.remainingSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (current.isFinished) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Row(horizontalArrangement = Arrangement.Center) {
                    if (!current.isFinished) {
                        IconButton(
                            onClick = { if (current.isRunning) viewModel.pause() else viewModel.resume() },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = if (current.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (current.isRunning) {
                                        R.string.step_timer_pause_content_description
                                    } else {
                                        R.string.step_timer_resume_content_description
                                    }
                                ),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    IconButton(onClick = viewModel::cancel, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.step_timer_cancel_content_description),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** "M:SS" under an hour, "H:MM:SS" at or above one hour. */
private fun formatTimerDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
