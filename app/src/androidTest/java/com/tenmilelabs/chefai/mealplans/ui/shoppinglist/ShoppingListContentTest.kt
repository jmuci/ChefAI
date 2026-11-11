package com.tenmilelabs.chefai.mealplans.ui.shoppinglist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasSetTextAction
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingList
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListItem
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The long-press → pencil/X → rename/remove interaction, driven through the stateless
 * [ShoppingListContent] with plain parameters — no Hilt, no ViewModel.
 */
class ShoppingListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val onion = ShoppingListItem("onion", "Onion", "2", GrocerySection.PRODUCE, isChecked = false)

    private val list = ShoppingList(listOf(ShoppingListSection(GrocerySection.PRODUCE, listOf(onion))))

    private val actions = mutableListOf<ShoppingListAction>()

    private fun setContent() {
        actions.clear()
        composeTestRule.setContent {
            ChefAITheme { ShoppingListContent(list = list, onAction = { actions += it }) }
        }
    }

    private fun longPressOnion() {
        composeTestRule.onNodeWithText("Onion").performTouchInput { longClick() }
    }

    @Test
    fun actions_areHiddenUntilALongPress() {
        setContent()

        composeTestRule.onNodeWithContentDescription("Remove Onion").assertDoesNotExist()
        longPressOnion()

        composeTestRule.onNodeWithContentDescription("Remove Onion").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Rename Onion").assertIsDisplayed()
        assertTrue("a long press must not tick the row", actions.isEmpty())
    }

    @Test
    fun tappingARevealedRow_hidesTheActionsWithoutTicking() {
        setContent()
        longPressOnion()

        composeTestRule.onNodeWithText("Onion").performClick()

        composeTestRule.onNodeWithContentDescription("Remove Onion").assertDoesNotExist()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun tappingARowWithoutActions_ticksIt() {
        setContent()

        composeTestRule.onNodeWithText("Onion").performClick()

        assertEquals(listOf<ShoppingListAction>(ShoppingListAction.Toggle(onion)), actions)
    }

    @Test
    fun tappingX_emitsRemove_andHidesTheActions() {
        setContent()
        longPressOnion()

        composeTestRule.onNodeWithContentDescription("Remove Onion").performClick()

        assertEquals(listOf<ShoppingListAction>(ShoppingListAction.Remove(onion)), actions)
        composeTestRule.onNodeWithContentDescription("Remove Onion").assertDoesNotExist()
    }

    @Test
    fun renameDialog_savesTheTypedName() {
        setContent()
        longPressOnion()
        composeTestRule.onNodeWithContentDescription("Rename Onion").performClick()

        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Red onions")
        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals(listOf<ShoppingListAction>(ShoppingListAction.Rename(onion, "Red onions")), actions)
    }

    @Test
    fun renameDialog_cannotSaveABlankName() {
        setContent()
        longPressOnion()
        composeTestRule.onNodeWithContentDescription("Rename Onion").performClick()

        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNodeWithText("Save").performClick()

        assertTrue(actions.isEmpty())
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExist() = assertDoesNotExist()
}
