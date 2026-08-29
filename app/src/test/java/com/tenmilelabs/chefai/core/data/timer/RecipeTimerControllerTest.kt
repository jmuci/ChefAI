package com.tenmilelabs.chefai.core.data.timer

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Note on the `advanceTimeBy` + `runCurrent()` pairing below: [kotlinx.coroutines.test.TestScope]'s
 * `advanceTimeBy(n)` does *not* run a task scheduled exactly at the new virtual time — only ones
 * strictly before it. Since [RecipeTimerController] ticks via a repeated `delay(1_000)`, advancing
 * by an exact multiple of a second lands right on a pending tick, so an explicit `runCurrent()` is
 * needed to actually execute it.
 *
 * [RecipeTimerController] deliberately launches its tick loop on `Dispatchers.Main` (see its KDoc),
 * so [mainCoroutineRule] wires that to the *same* [testDispatcher] instance backing [testScope] —
 * one shared virtual clock, so `advanceTimeBy`/`runCurrent` on the scope also drive the
 * Main-dispatched tick coroutine deterministically.
 */
@ExperimentalCoroutinesApi
class RecipeTimerControllerTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(testDispatcher)

    private lateinit var controller: RecipeTimerController
    private lateinit var notifier: RecipeTimerNotifier
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        testScope = TestScope(testDispatcher)
        notifier = mockk(relaxed = true)
        controller = RecipeTimerController(applicationScope = testScope, notifier = notifier)
    }

    @Test
    fun `start sets the initial running state`() {
        controller.start(stepLabel = "Bake", totalSeconds = 30)

        val state = controller.state.value
        assertThat(state?.stepLabel).isEqualTo("Bake")
        assertThat(state?.totalSeconds).isEqualTo(30)
        assertThat(state?.remainingSeconds).isEqualTo(30)
        assertThat(state?.isRunning).isTrue()
    }

    @Test
    fun `ignores a non-positive duration`() {
        controller.start(stepLabel = "Bake", totalSeconds = 0)

        assertThat(controller.state.value).isNull()
    }

    @Test
    fun `start reports the previous timer it replaced, if any`() {
        assertThat(controller.start(stepLabel = "Bake", totalSeconds = 10)).isNull()

        val replaced = controller.start(stepLabel = "Simmer", totalSeconds = 20)

        assertThat(replaced?.stepLabel).isEqualTo("Bake")
    }

    @Test
    fun `ticks down once per second`() = testScope.runTest {
        controller.start(stepLabel = "Bake", totalSeconds = 10)

        advanceTimeBy(3_500)
        runCurrent()

        assertThat(controller.state.value?.remainingSeconds).isEqualTo(7)
    }

    @Test
    fun `notifies and stops running when it reaches zero`() = testScope.runTest {
        controller.start(stepLabel = "Bake", totalSeconds = 2)

        advanceUntilIdle()

        val state = controller.state.value
        assertThat(state?.remainingSeconds).isEqualTo(0)
        assertThat(state?.isRunning).isFalse()
        verify(exactly = 1) { notifier.notifyTimerComplete("Bake") }
    }

    @Test
    fun `pause stops the countdown without clearing state`() = testScope.runTest {
        controller.start(stepLabel = "Bake", totalSeconds = 10)
        advanceTimeBy(2_500)

        controller.pause()
        val pausedRemaining = controller.state.value?.remainingSeconds

        advanceTimeBy(5_000)

        assertThat(controller.state.value?.isRunning).isFalse()
        assertThat(controller.state.value?.remainingSeconds).isEqualTo(pausedRemaining)
    }

    @Test
    fun `resume continues the countdown from where it was paused`() = testScope.runTest {
        controller.start(stepLabel = "Bake", totalSeconds = 10)
        advanceTimeBy(2_500)
        controller.pause()
        val pausedRemaining = requireNotNull(controller.state.value?.remainingSeconds)

        controller.resume()
        advanceTimeBy(1_000)
        runCurrent()

        assertThat(controller.state.value?.isRunning).isTrue()
        assertThat(controller.state.value?.remainingSeconds).isEqualTo(pausedRemaining - 1)
    }

    @Test
    fun `cancel clears the timer entirely`() = testScope.runTest {
        controller.start(stepLabel = "Bake", totalSeconds = 10)
        advanceTimeBy(1_000)

        controller.cancel()

        assertThat(controller.state.value).isNull()
    }

    @Test
    fun `starting a new timer replaces the running one`() = testScope.runTest {
        controller.start(stepLabel = "Bake", totalSeconds = 10)
        advanceTimeBy(3_000)
        runCurrent()

        controller.start(stepLabel = "Simmer", totalSeconds = 20)
        advanceTimeBy(1_000)
        runCurrent()

        val state = controller.state.value
        assertThat(state?.stepLabel).isEqualTo("Simmer")
        assertThat(state?.remainingSeconds).isEqualTo(19)
    }
}
