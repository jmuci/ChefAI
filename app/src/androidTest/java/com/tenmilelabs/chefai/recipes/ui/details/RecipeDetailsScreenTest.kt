package com.tenmilelabs.chefai.recipes.ui.details

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.preview.RecipeData
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.recipes.domain.scaling.RecipeScaling
import com.tenmilelabs.chefai.recipes.ui.details.components.DECREASE_SERVINGS_TAG
import com.tenmilelabs.chefai.recipes.ui.details.components.INCREASE_SERVINGS_TAG
import com.tenmilelabs.chefai.recipes.ui.details.components.SERVINGS_COUNT_TAG
import junit.framework.TestCase.assertEquals
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
    fun deleteButton_hidden_whenTheCallerCannotDelete() {
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(recipe = RecipeData.recipe, canDelete = false)
            }
        }

        composeTestRule.onNodeWithTag("DeleteRecipeButton").assertDoesNotExist()
    }

    @Test
    fun deleteButton_shown_andEmitsAction_whenTheCallerCanDelete() {
        val actions = mutableListOf<RecipeDetailsAction>()
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    canDelete = true,
                    onAction = { actions += it },
                )
            }
        }

        composeTestRule.onNodeWithTag("DeleteRecipeButton").assertIsDisplayed().performClick()

        assertEquals(listOf(RecipeDetailsAction.DeleteClicked), actions)
    }

    @Test
    fun deleteButton_disabled_emitsNoAction_whenIsDeleting() {
        val actions = mutableListOf<RecipeDetailsAction>()
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    canDelete = true,
                    isDeleting = true,
                    onAction = { actions += it },
                )
            }
        }

        composeTestRule.onNodeWithTag("DeleteRecipeButton")
            .assertIsNotEnabled()
            .performClick()

        assertTrue(actions.isEmpty())
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
    fun cookedToggle_shown_andEmitsAction_whenProvided() {
        val actions = mutableListOf<RecipeDetailsAction>()
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    showCookedToggle = true,
                    isCooked = false,
                    onAction = { actions += it },
                )
            }
        }

        composeTestRule.onNodeWithTag("CookedToggleButton").assertIsDisplayed().performClick()

        assertEquals(listOf(RecipeDetailsAction.ToggleCooked), actions)
    }

    @Test
    fun confirmationDialog_notShown_byDefault() {
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    canDelete = true,
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
                    canDelete = true,
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
    fun confirmationDialog_confirm_emitsConfirmDelete() {
        val actions = mutableListOf<RecipeDetailsAction>()
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    canDelete = true,
                    showDeleteConfirmation = true,
                    onAction = { actions += it },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.delete_recipe_button)).performClick()

        assertEquals(listOf(RecipeDetailsAction.ConfirmDelete), actions)
    }

    @Test
    fun confirmationDialog_cancel_emitsDismissDeleteDialog() {
        val actions = mutableListOf<RecipeDetailsAction>()
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    canDelete = true,
                    showDeleteConfirmation = true,
                    onAction = { actions += it },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.cancel_button)).performClick()

        assertEquals(listOf(RecipeDetailsAction.DismissDeleteDialog), actions)
    }

    // --- Portions stepper -----------------------------------------------------------------------

    @Test
    fun servingsStepper_showsTheRecipesOwnYield_byDefault() {
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(recipe = RecipeData.recipe)
            }
        }

        composeTestRule.onNodeWithTag(SERVINGS_COUNT_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(
                R.plurals.portions_count,
                RecipeData.recipe.servings,
                RecipeData.recipe.servings,
            )
        ).assertIsDisplayed()
    }

    @Test
    fun servingsStepper_buttonsReportTheNextCount() {
        val actions = mutableListOf<RecipeDetailsAction>()
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    servings = servingsState(current = 4),
                    onAction = { actions += it },
                )
            }
        }

        composeTestRule.onNodeWithTag(INCREASE_SERVINGS_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(DECREASE_SERVINGS_TAG).performClick()

        assertEquals(
            listOf(
                RecipeDetailsAction.ServingsChanged(5),
                RecipeDetailsAction.ServingsChanged(3),
            ),
            actions,
        )
    }

    @Test
    fun servingsStepper_decreaseDisabled_atTheBottomOfTheRange() {
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    servings = servingsState(current = 1),
                )
            }
        }

        composeTestRule.onNodeWithTag(DECREASE_SERVINGS_TAG).performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag(INCREASE_SERVINGS_TAG).assertIsEnabled()
    }

    @Test
    fun servingsStepper_increaseDisabled_atTheTopOfTheRange() {
        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    servings = servingsState(current = 10),
                )
            }
        }

        composeTestRule.onNodeWithTag(INCREASE_SERVINGS_TAG).performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag(DECREASE_SERVINGS_TAG).assertIsEnabled()
    }

    @Test
    fun servingsStepper_estimatedCaption_shownOnlyWhenTheYieldWasAssumed() {
        val caption = context.getString(R.string.portions_estimated, RecipeScaling.DEFAULT_SERVINGS)

        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe.copy(servings = 0),
                    servings = servingsState(current = 4, isEstimated = true),
                )
            }
        }

        composeTestRule.onNodeWithText(caption).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun servingsStepper_estimatedCaption_hiddenWhenTheRecipeHasAYield() {
        val caption = context.getString(R.string.portions_estimated, RecipeScaling.DEFAULT_SERVINGS)

        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(recipe = RecipeData.recipe)
            }
        }

        composeTestRule.onNodeWithText(caption).assertDoesNotExist()
    }

    @Test
    fun ingredientsTab_scalesTheQuantitiesToTheChosenPortions() {
        // Eggs, not spaghetti: doubled Pecorino (250 -> 500 gr) collides with spaghetti's own
        // unscaled "500 gr", which would make the negative assertion below meaningless.
        val eggs = RecipeData.recipe.ingredients.single { it.ingredientDisplayName == "Eggs" }

        composeTestRule.setContent {
            ChefAITheme {
                RecipeDetailsContent(
                    recipe = RecipeData.recipe,
                    servings = servingsState(current = RecipeData.recipe.servings * 2),
                )
            }
        }

        composeTestRule.onNodeWithText("${eggs.quantity.toInt() * 2} ${eggs.unit}")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("${eggs.quantity.toInt()} ${eggs.unit}")
            .assertDoesNotExist()
    }

    /** The recipe fixture's own yield, with [current] selected on top of it. */
    private fun servingsState(current: Int, isEstimated: Boolean = false) =
        ServingsUiState.forRecipeServings(RecipeData.recipe.servings)
            .copy(current = current, isEstimated = isEstimated)
}
