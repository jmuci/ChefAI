package com.tenmilelabs.chefai.search.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [CategoryCard]. Stateless composable, exercised directly with plain
 * parameters — no Hilt, no ViewModel — mirroring [com.tenmilelabs.chefai.core.ui.components.RecipeListCardTest].
 */
class CategoryCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun title_isDisplayed() {
        composeTestRule.setContent {
            ChefAITheme {
                CategoryCard(title = "Breakfast", onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Breakfast").assertIsDisplayed()
    }

    @Test
    fun click_invokesCallback() {
        var clicked = false
        composeTestRule.setContent {
            ChefAITheme {
                CategoryCard(title = "Breakfast", onClick = { clicked = true })
            }
        }

        composeTestRule.onNodeWithText("Breakfast").performClick()

        assertTrue("onClick was not invoked", clicked)
    }

    @Test
    fun longTitle_doesNotCrash_andRenders() {
        // Regression guard for a long, two-line-wrapping title ("Sandwiches & Wraps" is the
        // longest label in SearchCategory) on a narrow card, the shape CategoryCard takes inside
        // a two-column LazyVerticalGrid.
        composeTestRule.setContent {
            ChefAITheme {
                CategoryCard(
                    title = "Sandwiches & Wraps",
                    onClick = {},
                    modifier = Modifier.width(160.dp),
                )
            }
        }

        composeTestRule.onNodeWithTag("CategoryCard")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(1.dp)
        composeTestRule.onNodeWithText("Sandwiches & Wraps").assertIsDisplayed()
    }
}
