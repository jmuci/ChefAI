package com.tenmilelabs.chefai.core.data.local.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NextUpdatedAtTest {

    @Test
    fun `a new row takes the clock as it is`() {
        assertThat(nextUpdatedAt(previous = null, now = 1_000L)).isEqualTo(1_000L)
    }

    @Test
    fun `an edit on a clock that is ahead of the stored version takes the clock`() {
        assertThat(nextUpdatedAt(previous = 1_000L, now = 5_000L)).isEqualTo(5_000L)
    }

    @Test
    fun `an edit on a clock behind the stored version still sorts after it`() {
        assertThat(nextUpdatedAt(previous = 5_000L, now = 1_000L)).isEqualTo(5_001L)
    }

    @Test
    fun `an edit in the same millisecond as the stored version still sorts after it`() {
        assertThat(nextUpdatedAt(previous = 5_000L, now = 5_000L)).isEqualTo(5_001L)
    }
}
