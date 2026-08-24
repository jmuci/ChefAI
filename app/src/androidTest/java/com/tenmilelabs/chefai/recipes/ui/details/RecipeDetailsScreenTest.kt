package com.tenmilelabs.chefai.recipes.ui.details

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.preview.RecipeData
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the delete button and confirmation dialog added to
 * [RecipeDetailsContent]. Exercises the stateless composable directly with plain parameters —
 * no Hilt, no ViewModel, no Activity — covering the conditional rendering and click wiring that
 * the ViewModel-level tests in [RecipeDetailsViewModelTest] can't reach.
 */
class RecipeDetailsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun deleteButton_hidden_whenOnDeleteClickIsNull() {
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(recipe = RecipeData.recipe, onDeleteClick = null)
            }
        }

        composeTestRule.onNodeWithTag("DeleteRecipeButton").assertDoesNotExist()
    }

    @Test
    fun deleteButton_shown_andInvokesCallback_whenProvided() {
        var clicked = false
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    onDeleteClick = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("DeleteRecipeButton").assertIsDisplayed().performClick()

        assertTrue(clicked)
    }

    @Test
    fun deleteButton_disabled_doesNotInvokeCallback_whenIsDeleting() {
        var clicked = false
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    onDeleteClick = { clicked = true },
                    isDeleting = true,
                )
            }
        }

        composeTestRule.onNodeWithTag("DeleteRecipeButton")
            .assertIsNotEnabled()
            .performClick()

        assertFalse(clicked)
    }

    @Test
    fun cookedToggle_hidden_whenShowCookedToggleIsFalse() {
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(recipe = RecipeData.recipe, showCookedToggle = false)
            }
        }

        composeTestRule.onNodeWithTag("CookedToggleButton").assertDoesNotExist()
    }

    @Test
    fun cookedToggle_shown_andInvokesCallback_whenProvided() {
        var toggled = false
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    showCookedToggle = true,
                    isCooked = false,
                    onToggleCooked = { toggled = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("CookedToggleButton").assertIsDisplayed().performClick()

        assertTrue(toggled)
    }

    @Test
    fun confirmationDialog_notShown_byDefault() {
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    onDeleteClick = {},
                    showDeleteConfirmation = false,
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.delete_recipe_confirmation_title))
            .assertDoesNotExist()
    }

    @Test
    fun confirmationDialog_shown_whenFlagTrue() {
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    onDeleteClick = {},
                    showDeleteConfirmation = true,
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.delete_recipe_confirmation_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.delete_recipe_confirmation_message))
            .assertIsDisplayed()
    }

    @Test
    fun confirmationDialog_confirm_invokesOnConfirmDelete() {
        var confirmed = false
        var dismissed = false
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    onDeleteClick = {},
                    showDeleteConfirmation = true,
                    onConfirmDelete = { confirmed = true },
                    onDismissDeleteDialog = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.delete_recipe_button)).performClick()

        assertTrue(confirmed)
        assertFalse(dismissed)
    }

    @Test
    fun confirmationDialog_cancel_invokesOnDismissDeleteDialog() {
        var confirmed = false
        var dismissed = false
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    onDeleteClick = {},
                    showDeleteConfirmation = true,
                    onConfirmDelete = { confirmed = true },
                    onDismissDeleteDialog = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.cancel_button)).performClick()

        assertTrue(dismissed)
        assertFalse(confirmed)
    }
}
