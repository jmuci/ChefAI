package com.tenmilelabs.chefai.search.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.search.ui.model.SearchCategory
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [SearchBrowseContent]. Exercises the stateless composable directly with a
 * plain callback — no Hilt, no ViewModel — the same shape as [CategoryCardTest] and
 * [com.tenmilelabs.chefai.core.ui.components.RecipeListCardTest].
 */
class SearchBrowseContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun bothSectionHeaders_areDisplayed() {
        composeTestRule.setContent {
            ChefAITheme {
                SearchBrowseContent(onCategoryClick = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_section_by_meal))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_section_popular_categories))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun categoryClick_reportsTheTappedCategory() {
        var tapped: SearchCategory? = null
        composeTestRule.setContent {
            ChefAITheme {
                SearchBrowseContent(onCategoryClick = { tapped = it })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_category_breakfast))
            .performClick()

        assertEquals(SearchCategory.BREAKFAST, tapped)
    }

    @Test
    fun firstSection_rendersEveryMealCategory() {
        composeTestRule.setContent {
            ChefAITheme {
                SearchBrowseContent(onCategoryClick = {})
            }
        }

        val mealLabelResIds = listOf(
            R.string.search_category_breakfast,
            R.string.search_category_lunch,
            R.string.search_category_snack,
            R.string.search_category_dinner,
            R.string.search_category_dessert,
            R.string.search_category_kid_friendly,
        )

        mealLabelResIds.forEach { labelRes ->
            composeTestRule.onNodeWithText(context.getString(labelRes))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }
}
