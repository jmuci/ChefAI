package com.tenmilelabs.chefai.auth.ui.reset

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.tenmilelabs.chefai.auth.domain.TokenProvider
import com.tenmilelabs.chefai.auth.domain.model.PasswordResetResult
import com.tenmilelabs.chefai.auth.domain.repository.FakePasswordResetRepository
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import org.junit.Rule
import org.junit.Test

class ResetPasswordContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val repository = FakePasswordResetRepository()
    private val anonymous = object : TokenProvider {
        override fun getAccessToken(): String? = null
    }

    private fun show() {
        val viewModel = ResetPasswordViewModel(
            SavedStateHandle(mapOf(AppDestinationArgs.RESET_TOKEN_ARG to "tok")),
            repository,
            anonymous,
        )
        composeTestRule.setContent {
            ChefAITheme {
                val state by viewModel.uiState.collectAsState()
                ResetPasswordContent(state, viewModel::onAction, onNavigateBack = {})
            }
        }
    }

    @Test
    fun invalidToken_showsExpired_withRequestNewLink() {
        repository.validateResult = PasswordResetResult.InvalidToken
        show()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(hasTestTag("ResetPasswordExpired")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ResetPasswordRequestNewLink").assertIsDisplayed()
    }

    @Test
    fun validToken_editing_thenSubmit_showsDone() {
        show()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(hasTestTag("ResetPasswordNewField")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ResetPasswordNewField").performTextInput("hunter22!")
        composeTestRule.onNodeWithTag("ResetPasswordConfirmField").performTextInput("hunter22!")
        composeTestRule.onNodeWithTag("ResetPasswordSubmit").performClick()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(hasTestTag("ResetPasswordDone")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("ResetPasswordSignIn").assertIsDisplayed()
        assert(repository.confirmCalls == listOf("tok" to "hunter22!"))
    }
}
