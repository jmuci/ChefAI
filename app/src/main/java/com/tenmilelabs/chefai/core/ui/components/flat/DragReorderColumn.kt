package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * A drag-to-reorder list of rows — the ingredient and step rows in the recipe editor (screen 19).
 *
 * Not a `LazyColumn`: these lists are short and already live inside a screen-level scroll
 * container, so rows are plain children of a [Column]. Position math is resolved from each row's
 * *measured* height rather than an assumed fixed one, because a step's instruction text wraps to a
 * different number of lines than its neighbours. The underlying [items] list is reordered exactly
 * once, on drag end, via [onMove] — a gesture in progress never mutates it, so there is nothing for
 * a mid-drag recomposition to get out of sync with.
 *
 * ```
 * DragReorderColumn(items = ingredients, itemKey = { it.ingredientId }, onMove = { from, to -> … }) { ingredient, _, dragHandle ->
 *     IngredientRow(ingredient, dragHandle = dragHandle, onDelete = { … })
 * }
 * ```
 *
 * @param itemKey identity across recompositions — see [RuledGroup]'s `key` for why this matters.
 * @param onMove called once, on drag release, with the row's original and final index. Not called
 *   when a drag ends back where it started.
 * @param itemContent the row body. [dragHandle] is a `Modifier` that starts the drag when applied
 *   to the row's grip icon — nothing elsewhere in the row responds to the gesture.
 */
@Composable
fun <T> DragReorderColumn(
    items: List<T>,
    itemKey: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, index: Int, dragHandle: Modifier) -> Unit,
) {
    // Keyed on size alone: a row's height rarely changes shape between recompositions, and a stale
    // entry for an index that no longer exists is harmless — dragTargetIndex only reads in range.
    val heightsPx = remember { mutableStateMapOf<Int, Int>() }
    var drag by remember { mutableStateOf<DragGesture?>(null) }

    Column(modifier) {
        items.forEachIndexed { index, item ->
            key(itemKey(item)) {
                val activeDrag = drag
                val target = activeDrag?.let {
                    dragTargetIndex(it.startIndex, it.offsetPx, heightsPx, items.size)
                }
                val draggedHeight = activeDrag?.let { heightsPx[it.startIndex] } ?: 0
                val shiftPx = when {
                    activeDrag == null || target == null -> 0f
                    index == activeDrag.startIndex -> activeDrag.offsetPx
                    activeDrag.startIndex < target && index in (activeDrag.startIndex + 1)..target ->
                        -draggedHeight.toFloat()
                    activeDrag.startIndex > target && index in target until activeDrag.startIndex ->
                        draggedHeight.toFloat()
                    else -> 0f
                }

                Box(
                    modifier = Modifier
                        .zIndex(if (index == activeDrag?.startIndex) 1f else 0f)
                        .offset { IntOffset(0, shiftPx.roundToInt()) }
                        .onGloballyPositioned { heightsPx[index] = it.size.height },
                ) {
                    val dragHandle = Modifier.pointerInput(itemKey(item)) {
                        detectDragGestures(
                            onDragStart = { drag = DragGesture(startIndex = index, offsetPx = 0f) },
                            onDragEnd = {
                                drag?.let { gesture ->
                                    val to = dragTargetIndex(
                                        gesture.startIndex,
                                        gesture.offsetPx,
                                        heightsPx,
                                        items.size,
                                    )
                                    if (to != gesture.startIndex) onMove(gesture.startIndex, to)
                                }
                                drag = null
                            },
                            onDragCancel = { drag = null },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                drag = drag?.copy(offsetPx = drag!!.offsetPx + dragAmount.y)
                            },
                        )
                    }
                    itemContent(item, index, dragHandle)
                }
            }
        }
    }
}

private data class DragGesture(val startIndex: Int, val offsetPx: Float)

/**
 * Pure position math, kept free of Compose state so it can be unit tested directly: which index the
 * row dragged from [startIndex] currently sits over, given the accumulated [offsetPx] and every
 * row's measured [heightsPx].
 *
 * Walks one neighbour at a time, crossing into it only once the drag has covered *half* its height —
 * the same threshold every manual reorderable-list implementation uses, so the target flips exactly
 * when the dragged row visually overlaps its neighbour's midpoint rather than its leading edge.
 */
internal fun dragTargetIndex(
    startIndex: Int,
    offsetPx: Float,
    heightsPx: Map<Int, Int>,
    itemCount: Int,
): Int {
    var index = startIndex
    var remaining = offsetPx
    if (remaining > 0f) {
        while (index < itemCount - 1) {
            val nextHeight = heightsPx[index + 1] ?: break
            if (remaining <= nextHeight / 2f) break
            remaining -= nextHeight
            index++
        }
    } else {
        while (index > 0) {
            val previousHeight = heightsPx[index - 1] ?: break
            if (-remaining <= previousHeight / 2f) break
            remaining += previousHeight
            index--
        }
    }
    return index
}
