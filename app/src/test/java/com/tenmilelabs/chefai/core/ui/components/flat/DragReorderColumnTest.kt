package com.tenmilelabs.chefai.core.ui.components.flat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DragReorderColumnTest {

    /** Three 40px rows: 0..40, 40..80, 80..120. */
    private val heights = mapOf(0 to 40, 1 to 40, 2 to 40)

    @Test
    fun `no movement stays on the start index`() {
        assertThat(dragTargetIndex(startIndex = 1, offsetPx = 0f, heightsPx = heights, itemCount = 3))
            .isEqualTo(1)
    }

    @Test
    fun `dragging past the halfway point of the next row crosses into it`() {
        // Row 1 dragged down: needs to clear half of row 2's height (20px) to land on it.
        assertThat(dragTargetIndex(1, offsetPx = 19f, heights, 3)).isEqualTo(1)
        assertThat(dragTargetIndex(1, offsetPx = 21f, heights, 3)).isEqualTo(2)
    }

    @Test
    fun `dragging past the halfway point of the previous row crosses into it`() {
        assertThat(dragTargetIndex(1, offsetPx = -19f, heights, 3)).isEqualTo(1)
        assertThat(dragTargetIndex(1, offsetPx = -21f, heights, 3)).isEqualTo(0)
    }

    @Test
    fun `cannot move past the ends of the list`() {
        assertThat(dragTargetIndex(2, offsetPx = 1000f, heights, 3)).isEqualTo(2)
        assertThat(dragTargetIndex(0, offsetPx = -1000f, heights, 3)).isEqualTo(0)
    }

    @Test
    fun `jumping two rows in one gesture lands on the far row`() {
        // From row 0, clearing rows 1 and 2's halfway points lands on row 2.
        assertThat(dragTargetIndex(0, offsetPx = 40f + 21f, heights, 3)).isEqualTo(2)
    }

    @Test
    fun `an unmeasured neighbour stops the walk rather than crashing`() {
        assertThat(dragTargetIndex(0, offsetPx = 1000f, heightsPx = emptyMap(), itemCount = 3))
            .isEqualTo(0)
    }
}
