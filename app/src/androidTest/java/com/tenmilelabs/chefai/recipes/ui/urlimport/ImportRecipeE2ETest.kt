package com.tenmilelabs.chefai.recipes.ui.urlimport

import android.content.ClipData
import android.content.ClipboardManager
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
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipeImporter
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * End-to-end UI tests for the recipe URL import flow.
 *
 * These exercise the real navigation graph, the real [ImportRecipeViewModel], and real Room-backed
 * draft persistence (via [com.tenmilelabs.chefai.core.di.TestDatabaseModule]'s in-memory database).
 * Only [FakeRecipeImporter] is swapped in — real fetch/parse behaviour (network errors, HTML
 * extraction, URL validation) is covered by
 * [com.tenmilelabs.chefai.recipes.data.repository.DefaultRecipeImporterTest] with Ktor `MockEngine`.
 */
@HiltAndroidTest
class ImportRecipeE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var fakeRecipeImporter: FakeRecipeImporter

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        fakeRecipeImporter.reset()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // The screen prefills its field from the clipboard on first composition; clear it so
        // whatever's left over on the device/emulator from unrelated use can't leak into a test.
        clearClipboard()
    }

    @After
    fun tearDown() {
        fakeRecipeImporter.reset()
        clearClipboard()
    }

    private fun clearClipboard() {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
    }

    @OptIn(ExperimentalTestApi::class)
    private fun navigateToImportScreenViaFab() {
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("HomeScreen"), 5_000)

        composeTestRule.onNodeWithText(context.getString(R.string.app_dest_title_recipes)).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("RecipesScreen"), 5_000)

        composeTestRule.onNodeWithTag("RecipesFabToggle").performClick()
        composeTestRule.onNodeWithTag("ImportRecipeFabItem").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("ImportRecipeScreen"), 5_000)
    }

    /**
     * Given: a successful scrape result queued on the fake importer
     * When: the user reaches the import screen via the FAB, enters a URL and taps Import
     * Then: the app navigates to the recipe editor — the draft was persisted (real Room) and the
     *   nav effect fired
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun successfulImport_navigatesToTheRecipeEditor() {
        val draftId = UuidV7Generator.newId()
        fakeRecipeImporter.result = RecipeImportResult.Success(
            RecipeDraft(recipeId = draftId, isNewRecipe = true, title = "Imported Recipe"),
        )

        navigateToImportScreenViaFab()

        composeTestRule.onNodeWithTag("ImportUrlField").performTextInput("https://example.com/recipe")
        composeTestRule.onNodeWithTag("ImportButton").performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("RecipeEditorScreen"), 5_000)

        assertEquals("https://example.com/recipe", fakeRecipeImporter.lastImportedUrl)
    }

    /**
     * Given: the fake importer configured to report an invalid URL
     * When: the user taps Import
     * Then: an error message is shown and the user stays on the import screen (no nav effect)
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun invalidUrl_showsAnErrorAndStaysOnTheImportScreen() {
        fakeRecipeImporter.result = RecipeImportResult.InvalidUrl

        navigateToImportScreenViaFab()

        composeTestRule.onNodeWithTag("ImportUrlField").performTextInput("not a url")
        composeTestRule.onNodeWithTag("ImportButton").performClick()

        composeTestRule
            .onNodeWithText(context.getString(R.string.import_recipe_invalid_url))
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("ImportRecipeScreen").assertIsDisplayed()
    }

    /**
     * Given: the fake importer configured to report no recipe was found on the page
     * When: the user taps Import
     * Then: the "enter it manually" offramp is shown alongside the error
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noRecipeFound_offersManualEntry() {
        fakeRecipeImporter.result = RecipeImportResult.NoRecipeFound

        navigateToImportScreenViaFab()

        composeTestRule.onNodeWithTag("ImportUrlField").performTextInput("https://example.com/not-a-recipe")
        composeTestRule.onNodeWithTag("ImportButton").performClick()

        composeTestRule
            .onNodeWithText(context.getString(R.string.import_recipe_no_recipe_found))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(context.getString(R.string.import_recipe_enter_manually))
            .assertIsDisplayed()
    }
}
