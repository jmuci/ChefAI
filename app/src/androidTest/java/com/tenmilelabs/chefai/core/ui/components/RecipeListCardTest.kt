package com.tenmilelabs.chefai.core.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.collections.domain.model.BookmarkUpdate
import com.tenmilelabs.chefai.collections.domain.model.RecipeBookmark
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the bookmark affordance and [Modifier] parameter added to
 * [RecipeListCard] for search results. Exercises the stateless composable directly with plain
 * parameters — no Hilt, no ViewModel — covering the conditional rendering the ViewModel-level
 * search tests can't reach.
 */
class RecipeListCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bookmarkButton_absent_whenOnBookmarkUpdateIsNull() {
        // Regression guard: rows that don't offer inline saving must stay visually unchanged.
        composeTestRule.setContent {
            ChefAITheme {
                RecipeListCard(recipe = PreviewData.grilledChickenRecipe)
            }
        }

        composeTestRule.onNodeWithTag("SaveToCollectionButton").assertDoesNotExist()
    }

    @Test
    fun bookmarkButton_opensMenu_andBookmarkRowEmitsBookmark() {
        var update: BookmarkUpdate? = null
        composeTestRule.setContent {
            ChefAITheme {
                RecipeListCard(
                    recipe = PreviewData.grilledChickenRecipe,
                    onBookmarkUpdate = { update = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("SaveToCollectionButton").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("BookmarkMenuStar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BookmarkMenuBookmark").performClick()

        assertEquals(BookmarkUpdate.Bookmark, update)
    }

    @Test
    fun bookmarkedRecipe_menuOffersStarAndRemove() {
        var update: BookmarkUpdate? = null
        composeTestRule.setContent {
            ChefAITheme {
                RecipeListCard(
                    recipe = PreviewData.grilledChickenRecipe,
                    bookmark = RecipeBookmark(PreviewData.grilledChickenRecipe.uuid),
                    onBookmarkUpdate = { update = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("SaveToCollectionButton").performClick()
        composeTestRule.onNodeWithTag("BookmarkMenuStar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BookmarkMenuRemove").performClick()

        assertEquals(BookmarkUpdate.Remove, update)
    }

    @Test
    fun goToToggle_shownOnlyOnceBookmarked_andEmitsSetGoTo() {
        var bookmark by mutableStateOf<RecipeBookmark?>(null)
        var update: BookmarkUpdate? = null
        composeTestRule.setContent {
            ChefAITheme {
                RecipeListCard(
                    recipe = PreviewData.grilledChickenRecipe,
                    bookmark = bookmark,
                    onBookmarkUpdate = { update = it },
                )
            }
        }
        composeTestRule.onNodeWithTag("GoToToggleButton").assertDoesNotExist()

        bookmark = RecipeBookmark(PreviewData.grilledChickenRecipe.uuid)
        composeTestRule.onNodeWithTag("GoToToggleButton").assertIsDisplayed().performClick()

        assertEquals(BookmarkUpdate.SetGoTo(true), update)
    }

    @Test
    fun bookmarkButton_announcesState() {
        // The glyph is the only other signal of the saved state; a screen reader needs the label.
        var bookmark by mutableStateOf<RecipeBookmark?>(null)
        composeTestRule.setContent {
            ChefAITheme {
                RecipeListCard(
                    recipe = PreviewData.grilledChickenRecipe,
                    bookmark = bookmark,
                    onBookmarkUpdate = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Save to collection").assertIsDisplayed()

        bookmark = RecipeBookmark(PreviewData.grilledChickenRecipe.uuid)
        composeTestRule.onNodeWithContentDescription("Saved to collection").assertIsDisplayed()

        bookmark = RecipeBookmark(PreviewData.grilledChickenRecipe.uuid, isStarred = true)
        composeTestRule.onNodeWithContentDescription("Starred in collection").assertIsDisplayed()
    }

    @Test
    fun modifier_parameter_isMerged_notShadowed() {
        // A caller-supplied modifier (here, a testTag) must actually reach the rendered row —
        // proving RecipeListCard chains its own fillMaxWidth onto the incoming modifier rather
        // than replacing it outright.
        composeTestRule.setContent {
            ChefAITheme {
                RecipeListCard(
                    recipe = PreviewData.grilledChickenRecipe,
                    modifier = Modifier.testTag("CustomCardModifier").height(500.dp),
                )
            }
        }

        // The node must exist and be displayed at all, which is only true if the incoming
        // modifier was actually applied rather than dropped.
        composeTestRule.onNodeWithTag("CustomCardModifier")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(1.dp)
    }
}
