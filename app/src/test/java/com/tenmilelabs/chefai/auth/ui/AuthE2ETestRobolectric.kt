package com.tenmilelabs.chefai.auth.ui

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.tenmilelabs.chefai.MainActivity
import com.tenmilelabs.chefai.R.string
import com.tenmilelabs.chefai.auth.data.network.FakeAuthNetworkDataSource
import com.tenmilelabs.chefai.auth.data.network.dto.AuthResponse
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import javax.inject.Inject

/**
 * Robolectric-based UI tests for authentication flows.
 *
 * These tests exercise the full authentication flow using Robolectric
 * to run on the JVM instead of an emulator, suitable for CI environments.
 *
 * Uses:
 * - Real navigation graph
 * - Real ViewModels
 * - Fake network and storage dependencies (no real network or database calls)
 *
 * The tests verify navigation by checking for Home screen UI content.
 */
@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
@Config(manifest = Config.NONE)
class AuthE2ETestRobolectric {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var fakeAuthDataSource: FakeAuthNetworkDataSource

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        fakeAuthDataSource.reset()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        fakeAuthDataSource.reset()
    }

    /**
     * Test: Existing user login
     *
     * Given: A fake auth repository with a predefined user
     * When: The user enters email and password and taps "Login"
     * Then: The app navigates to the Home screen
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun existingUserLogin_navigatesToHomeScreen() {
        val testEmail = "alice@example.com"
        val testPassword = "password123"
        val testUsername = "alice"

        fakeAuthDataSource.existingUsers[testEmail] = AuthResponse(
            token = "fake_access_token",
            refreshToken = "fake_refresh_token",
            userId = UUID.randomUUID().toString(),
            username = testUsername,
            email = testEmail,
            expiresIn = 3600
        )

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule.onNodeWithTag("UserProfileMenu").performClick()
        composeTestRule.onNodeWithText("Log in / Register").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("LoginScreen"), 5_000)

        composeTestRule.onNodeWithText("Email")
            .performTextInput(testEmail)

        composeTestRule.onNodeWithText("Password")
            .performTextInput(testPassword)

        composeTestRule.onNodeWithTag("LoginButton").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule
            .onNodeWithText(context.getString(string.home_recipe_suggestions_title))
            .assertIsDisplayed()
    }

    /**
     * Test: Register then login
     *
     * Given: No users initially
     * When: The user navigates to "Create account", registers with email and password, and logs in
     * Then: The app navigates to the Home screen after registration
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun registerThenLogin_navigatesToHomeScreen() {
        val testUsername = "bob"
        val testEmail = "bob@example.com"
        val testPassword = "password123"

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule.onNodeWithTag("UserProfileMenu").performClick()
        composeTestRule.onNodeWithText("Log in / Register").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("LoginScreen"), 5_000)

        composeTestRule.onNodeWithTag("CreateAccountButton").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("RegisterScreen"), 5_000)

        composeTestRule.onNodeWithText("Username")
            .performTextInput(testUsername)

        composeTestRule.onNodeWithText("Email")
            .performTextInput(testEmail)

        composeTestRule.onNodeWithText("Password")
            .performTextInput(testPassword)

        composeTestRule.onNodeWithText("Confirm Password")
            .performTextInput(testPassword)

        composeTestRule.onNodeWithTag("RegisterButton").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule.onNodeWithText(context.getString(string.home_recipe_suggestions_title))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("UserProfileMenu").performClick()

        composeTestRule.onNodeWithTag("LogoutMenuItem").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule.onNodeWithTag("UserProfileMenu").performClick()
        composeTestRule.onNodeWithText("Log in / Register").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("LoginScreen"), 5_000)

        composeTestRule.onNodeWithText("Email")
            .performTextInput(testEmail)

        composeTestRule.onNodeWithText("Password")
            .performTextInput(testPassword)

        composeTestRule.onNodeWithTag("LoginButton").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule.onNodeWithText(context.getString(string.home_recipe_suggestions_title))
            .assertIsDisplayed()
    }
}
