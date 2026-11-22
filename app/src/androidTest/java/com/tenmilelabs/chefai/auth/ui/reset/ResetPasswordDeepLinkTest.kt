package com.tenmilelabs.chefai.auth.ui.reset

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.tenmilelabs.chefai.MainActivity
import com.tenmilelabs.chefai.auth.domain.repository.FakePasswordResetRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

private const val TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE" // 43 chars of base64url

/**
 * Cold start from a `https://pocket-chef.app/reset-password?token=…` VIEW intent opens the reset
 * screen and validates that token. The repository is the shared fake from `TestAuthModule`, so
 * nothing touches the network. (Malformed links are covered by `ResetPasswordLinkParserTest`.)
 *
 * Launched with `startActivitySync` rather than `ActivityScenario`: the scenario's bootstrap
 * trampoline never sees `onCreate` for an https VIEW intent on API 36 and times out.
 */
@HiltAndroidTest
class ResetPasswordDeepLinkTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var repository: FakePasswordResetRepository

    private var activity: Activity? = null

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        activity?.finish()
    }

    @Test
    fun resetLink_opensTheResetScreen_andValidatesTheToken() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://pocket-chef.app/reset-password?token=$TOKEN"),
            instrumentation.targetContext,
            MainActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity = instrumentation.startActivitySync(intent)

        composeTestRule.waitUntil(15_000) {
            composeTestRule.onAllNodes(hasTestTag("ResetPasswordScreen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ResetPasswordScreen").assertIsDisplayed()
        composeTestRule.waitUntil(5_000) { repository.validateCalls.isNotEmpty() }
        assert(repository.validateCalls == listOf(TOKEN))
    }
}
