package com.tenmilelabs.chefai.auth.ui.forgot

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.SavedStateHandle
import com.tenmilelabs.chefai.auth.domain.repository.FakePasswordResetRepository
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import org.junit.Rule
import org.junit.Test

class ForgotPasswordContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val repository = FakePasswordResetRepository()

    private fun show(viewModel: ForgotPasswordViewModel) {
        composeTestRule.setContent {
            ChefAITheme {
                val state by viewModel.uiState.collectAsState()
                ForgotPasswordContent(state, viewModel::onAction, onNavigateBack = {})
            }
        }
    }

    @Test
    fun typeEmail_submit_showsSentState_withResendDisabled() {
        show(ForgotPasswordViewModel(SavedStateHandle(), repository))

        composeTestRule.onNodeWithTag("ForgotPasswordEmailField").performTextInput("alice@example.com")
        composeTestRule.onNodeWithTag("ForgotPasswordSubmit").performClick()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTagExists("ForgotPasswordSentBody")
        }
        composeTestRule.onNodeWithTag("ForgotPasswordSentBody").assertIsDisplayed()
            .assertTextContains("alice@example.com", substring = true)
        composeTestRule.onNodeWithTag("ForgotPasswordResend").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("ForgotPasswordBackToSignIn").assertIsEnabled()
    }

    @Test
    fun invalidEmail_staysOnForm_andDoesNotCallTheServer() {
        show(ForgotPasswordViewModel(SavedStateHandle(), repository))

        composeTestRule.onNodeWithTag("ForgotPasswordEmailField").performTextInput("nope")
        composeTestRule.onNodeWithTag("ForgotPasswordSubmit").performClick()

        composeTestRule.onNodeWithTag("ForgotPasswordSubmit").assertIsDisplayed()
        assert(repository.requestCalls.isEmpty())
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagExists(tag: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}
