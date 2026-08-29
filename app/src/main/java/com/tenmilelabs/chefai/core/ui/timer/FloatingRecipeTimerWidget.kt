package com.tenmilelabs.chefai.core.ui.timer

import android.content.res.Configuration
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.timer.RecipeTimerState
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import kotlin.math.roundToInt

private val WIDGET_SIZE = 88.dp
private val EDGE_PADDING = 16.dp

/**
 * App-wide floating widget for the active [RecipeTimerState]. Placed once, above the nav graph
 * (see `MainActivity`), so it survives navigating between screens.
 */
@Composable
fun FloatingRecipeTimerWidget(
    modifier: Modifier = Modifier,
    viewModel: RecipeTimerViewModel = hiltViewModel(),
) {
    val timerState by viewModel.state.collectAsStateWithLifecycle()
    FloatingRecipeTimerWidgetContent(
        state = timerState,
        onPauseResumeClick = { if (timerState?.isRunning == true) viewModel.pause() else viewModel.resume() },
        onCancelClick = viewModel::cancel,
        modifier = modifier,
    )
}

/**
 * Stateless content, hoisted out of [FloatingRecipeTimerWidget] so it's previewable without a Hilt
 * component. Renders nothing when [state] is null, but — deliberately — the surrounding
 * [BoxWithConstraints] and its remembered drag offset stay in composition either way: putting the
 * null check any higher than this (skipping `BoxWithConstraints` itself) would tear down the
 * remembered drag offset every time a timer finishes, resetting the widget to its default corner
 * on every fresh [RecipeTimerController.start][com.tenmilelabs.chefai.core.data.timer.RecipeTimerController.start]
 * instead of only on process death.
 */
@Composable
private fun FloatingRecipeTimerWidgetContent(
    state: RecipeTimerState?,
    onPauseResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val widgetSizePx = with(density) { WIDGET_SIZE.toPx() }
        val edgePaddingPx = with(density) { EDGE_PADDING.toPx() }
        val maxOffsetXPx = (maxWidthPx - widgetSizePx).coerceAtLeast(0f)
        val maxOffsetYPx = (maxHeightPx - widgetSizePx).coerceAtLeast(0f)

        var offsetX by remember { mutableFloatStateOf(maxWidthPx - widgetSizePx - edgePaddingPx) }
        var offsetY by remember { mutableFloatStateOf(maxHeightPx * 0.6f) }

        // Re-clamp whenever the available space changes (e.g. a rotation) so a position saved
        // near the old bottom/right edge can't end up outside the new bounds.
        LaunchedEffect(maxOffsetXPx, maxOffsetYPx) {
            offsetX = offsetX.coerceIn(0f, maxOffsetXPx)
            offsetY = offsetY.coerceIn(0f, maxOffsetYPx)
        }

        if (state != null) {
            Card(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(WIDGET_SIZE)
                    .testTag("RecipeTimerWidget")
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxOffsetXPx)
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxOffsetYPx)
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
                        text = formatTimerDuration(state.remainingSeconds),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isFinished) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Row(horizontalArrangement = Arrangement.Center) {
                        if (!state.isFinished) {
                            IconButton(
                                onClick = onPauseResumeClick,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(
                                        if (state.isRunning) {
                                            R.string.step_timer_pause_content_description
                                        } else {
                                            R.string.step_timer_resume_content_description
                                        }
                                    ),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        IconButton(onClick = onCancelClick, modifier = Modifier.size(28.dp)) {
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

@Preview(name = "Running", widthDp = 300, heightDp = 300, showBackground = true)
@Preview(
    name = "Running — dark",
    widthDp = 300,
    heightDp = 300,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FloatingRecipeTimerWidgetRunningPreview() {
    ChefAITheme {
        FloatingRecipeTimerWidgetContent(
            state = RecipeTimerState(
                stepLabel = "Step 2",
                totalSeconds = 120,
                remainingSeconds = 90,
                isRunning = true,
            ),
            onPauseResumeClick = {},
            onCancelClick = {},
        )
    }
}

@Preview(name = "Paused", widthDp = 300, heightDp = 300, showBackground = true)
@Composable
private fun FloatingRecipeTimerWidgetPausedPreview() {
    ChefAITheme {
        FloatingRecipeTimerWidgetContent(
            state = RecipeTimerState(
                stepLabel = "Step 2",
                totalSeconds = 120,
                remainingSeconds = 90,
                isRunning = false,
            ),
            onPauseResumeClick = {},
            onCancelClick = {},
        )
    }
}

@Preview(name = "Finished", widthDp = 300, heightDp = 300, showBackground = true)
@Composable
private fun FloatingRecipeTimerWidgetFinishedPreview() {
    ChefAITheme {
        FloatingRecipeTimerWidgetContent(
            state = RecipeTimerState(
                stepLabel = "Step 2",
                totalSeconds = 120,
                remainingSeconds = 0,
                isRunning = false,
            ),
            onPauseResumeClick = {},
            onCancelClick = {},
        )
    }
}
