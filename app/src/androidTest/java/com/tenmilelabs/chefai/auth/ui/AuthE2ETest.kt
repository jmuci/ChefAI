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
import androidx.test.platform.app.InstrumentationRegistry
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
import java.util.UUID
import javax.inject.Inject

/**
 * End-to-end UI tests for authentication flows.
 *
 * These tests exercise the full authentication flow including:
 * - Real navigation graph
 * - Real ViewModels
 * - Fake network and storage dependencies (no real network or database calls)
 *
 * The tests verify navigation by checking for Home screen UI content.
 */
@HiltAndroidTest
class AuthE2ETest {

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
        // Get reference to the fake data source
        fakeAuthDataSource.reset()
        context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
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
        // Given: Pre-seed a user in the fake auth data source
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

        // App starts on HomeScreen (anonymous-first model); navigate to Login via profile menu
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule.onNodeWithTag("UserProfileMenu").performClick()
        composeTestRule.onNodeWithText("Log in / Register").performClick()

        // Wait for the login screen to be displayed
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("LoginScreen"), 5_000)

        // When: User enters credentials and taps Login
        composeTestRule.onNodeWithText("Email")
            .performTextInput(testEmail)

        composeTestRule.onNodeWithText("Password")
            .performTextInput(testPassword)

        composeTestRule.onNodeWithTag("LoginButton").performClick()

        // Then: Home screen should be displayed
        // Wait for navigation to complete and verify we're on the home screen
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
        // Given: No pre-existing users (fakeAuthDataSource starts empty)
        val testUsername = "bob"
        val testEmail = "bob@example.com"
        val testPassword = "password123"

        // App starts on HomeScreen (anonymous-first model); navigate to Login via profile menu
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule.onNodeWithTag("UserProfileMenu").performClick()
        composeTestRule.onNodeWithText("Log in / Register").performClick()

        // Wait for login screen
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("LoginScreen"), 5_000)

        // When: User navigates to registration screen
        composeTestRule.onNodeWithTag("CreateAccountButton").performClick()

        // Wait for registration screen
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("RegisterScreen"), 5_000)

        // Fill in registration form
        composeTestRule.onNodeWithText("Username")
            .performTextInput(testUsername)

        composeTestRule.onNodeWithText("Email")
            .performTextInput(testEmail)

        // Find password field (first one)
        composeTestRule.onNodeWithText("Password")
            .performTextInput(testPassword)

        // Find confirm password field
        composeTestRule.onNodeWithText("Confirm Password")
            .performTextInput(testPassword)

        // Click register button
        composeTestRule.onNodeWithTag("RegisterButton").performClick()

        // Then: Should navigate to home screen directly after successful registration
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule.onNodeWithText(context.getString(string.home_recipe_suggestions_title))
            .assertIsDisplayed()

        // Additional verification: Logout and login with the same credentials
        // Click on the user profile menu
        composeTestRule.onNodeWithTag("UserProfileMenu").performClick()

        // Click logout
        composeTestRule.onNodeWithTag("LogoutMenuItem").performClick()

        // After logout, app returns to HomeScreen (anonymous mode)
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        // Navigate to Login via profile menu again
        composeTestRule.onNodeWithTag("UserProfileMenu").performClick()
        composeTestRule.onNodeWithText("Log in / Register").performClick()

        // Wait for login screen
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("LoginScreen"), 5_000)

        // Now login with the registered credentials
        composeTestRule.onNodeWithText("Email")
            .performTextInput(testEmail)

        composeTestRule.onNodeWithText("Password")
            .performTextInput(testPassword)

        composeTestRule.onNodeWithTag("LoginButton").performClick()

        // Verify we're back on the home screen
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule.onNodeWithText(context.getString(string.home_recipe_suggestions_title))
            .assertIsDisplayed()
    }
}
