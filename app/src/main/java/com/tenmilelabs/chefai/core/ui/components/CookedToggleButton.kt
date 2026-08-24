package com.tenmilelabs.chefai.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/**
 * The chef-hat toggle that marks a planned meal cooked. Filled once cooked, so a glance down a
 * list of these reads as a column of ticked-off hats.
 *
 * Shared between [com.tenmilelabs.chefai.mealplans.ui.components.MealPlanMealRow] and the recipe
 * details screen, which shows the same toggle when opened from a meal plan slot.
 */
@Composable
fun CookedToggleButton(
    isCooked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconToggleButton(
        checked = isCooked,
        onCheckedChange = { onToggle() },
        modifier = modifier
            .testTag("CookedToggleButton")
            .semantics { role = Role.Checkbox },
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chef_hat_black_24dp),
            contentDescription = stringResource(
                if (isCooked) R.string.meal_plan_mark_not_cooked else R.string.meal_plan_mark_cooked
            ),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Preview(name = "To cook — Light", showBackground = true)
@Composable
private fun CookedToggleButtonToCookPreview() {
    ChefAITheme(darkTheme = false) {
        CookedToggleButton(isCooked = false, onToggle = {})
    }
}

@Preview(name = "Cooked — Dark", showBackground = true)
@Composable
private fun CookedToggleButtonCookedPreview() {
    ChefAITheme(darkTheme = true) {
        CookedToggleButton(isCooked = true, onToggle = {})
    }
}
