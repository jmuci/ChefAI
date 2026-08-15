package com.tenmilelabs.chefai.core.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
    fun bookmarkButton_absent_whenOnSaveToCollectionIsNull() {
        // Regression guard: this is the default at this card's three existing call sites (Home,
        // Recipes tab, its own @Preview) — they must stay visually unchanged.
        composeTestRule.setContent {
            ChefAITheme {
                RecipeListCard(recipe = PreviewData.grilledChickenRecipe)
            }
        }

        composeTestRule.onNodeWithTag("SaveToCollectionButton").assertDoesNotExist()
    }

    @Test
    fun bookmarkButton_shown_andInvokesCallback_withTheRecipeUuid_whenProvided() {
        var savedId: java.util.UUID? = null
        composeTestRule.setContent {
            ChefAITheme {
                RecipeListCard(
                    recipe = PreviewData.grilledChickenRecipe,
                    onSaveToCollection = { savedId = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("SaveToCollectionButton").assertIsDisplayed().performClick()

        assertEquals(PreviewData.grilledChickenRecipe.uuid, savedId)
    }

    @Test
    fun modifier_parameter_isMerged_notShadowed() {
        // A caller-supplied modifier (here, a testTag) must actually reach the rendered Card —
        // proving RecipeListCard chains its own padding/height/fillMaxWidth onto the incoming
        // modifier rather than replacing it outright.
        composeTestRule.setContent {
            ChefAITheme {
                RecipeListCard(
                    recipe = PreviewData.grilledChickenRecipe,
                    modifier = Modifier.testTag("CustomCardModifier").height(500.dp),
                )
            }
        }

        // The card's own hardcoded height (R.dimen.recipe_card_height) is chained AFTER the
        // caller's modifier, so it wins — but the node must exist and be displayed at all, which
        // is only true if the incoming modifier was actually applied rather than dropped.
        composeTestRule.onNodeWithTag("CustomCardModifier")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(1.dp)
    }
}
