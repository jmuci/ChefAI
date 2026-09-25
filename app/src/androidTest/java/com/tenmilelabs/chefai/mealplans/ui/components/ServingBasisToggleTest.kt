package com.tenmilelabs.chefai.mealplans.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanServingBasis
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [ServingBasisToggle]. Stateless composable, exercised directly with plain
 * parameters, mirroring [com.tenmilelabs.chefai.search.ui.components.CategoryCardTest].
 */
class ServingBasisToggleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun heightStaysAtSegmentHeight_whenParentAllowsTheFullScreen() {
        // Regression: the Row had no intrinsic height, so its fillMaxHeight() VerticalDivider
        // stretched the control, and the Meal Plans top bar hosting it, to the full screen height.
        composeTestRule.setContent {
            ChefAITheme {
                Box(Modifier.fillMaxSize()) {
                    ServingBasisToggle(basis = MealPlanServingBasis.JUST_ME, onBasisChange = {})
                }
            }
        }

        val toggle = composeTestRule.onNodeWithContentDescription("Whose meals to show")
        toggle.assertHeightIsAtLeast(44.dp)
        val height = toggle.getUnclippedBoundsInRoot().height
        assertTrue("Toggle stretched to $height", height < 60.dp)
    }
}
