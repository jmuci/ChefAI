package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/**
 * The trailing grip that tells a row it can be reordered — Material Symbols' `drag_indicator`
 * (Lucide's `grip-vertical` in this icon set). Purely a visual affordance plus a touch target:
 * attach [reorderDrag] with `afterLongPress = false` so a drag starting here moves the row
 * immediately, while the rest of the row keeps the long-press drag.
 *
 * Decorative for accessibility — screen-reader users reorder through the row's custom
 * "Move earlier/later" actions instead, since a drag gesture is not reachable from TalkBack.
 */
@Composable
fun MealDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(DragHandleTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(ChefAIIcons.GripVertical),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Vertical reorder gesture shared by the plan detail list and the week view.
 *
 * Callbacks are read through [rememberUpdatedState] so a state emission mid-drag (a sync pull,
 * a cooked toggle elsewhere) does not restart `pointerInput` and silently cancel the drag.
 *
 * @param afterLongPress `true` for the row body (long-press to lift), `false` for [MealDragHandle].
 */
fun Modifier.reorderDrag(
    key: Any,
    afterLongPress: Boolean,
    onStart: () -> Unit,
    onDrag: (deltaY: Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
): Modifier = composed {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    pointerInput(key, afterLongPress) {
        if (afterLongPress) {
            detectDragGesturesAfterLongPress(
                onDragStart = { start() },
                onDrag = { change, amount ->
                    change.consume()
                    drag(amount.y)
                },
                onDragEnd = { end() },
                onDragCancel = { cancel() },
            )
        } else {
            detectDragGestures(
                onDragStart = { start() },
                onDrag = { change, amount ->
                    change.consume()
                    drag(amount.y)
                },
                onDragEnd = { end() },
                onDragCancel = { cancel() },
            )
        }
    }
}

/** M3's 48dp minimum touch target; the grip itself draws at 20dp. */
private val DragHandleTouchTarget = 48.dp

@Preview(name = "Drag handle — light")
@Preview(name = "Drag handle — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MealDragHandlePreview() {
    ChefAITheme {
        MealDragHandle(Modifier.background(MaterialTheme.colorScheme.background))
    }
}
