package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The trailing trash button that takes a meal out of its slot. Same 48dp target as
 * [MealDragHandle], so the two sit side by side on the row's end edge.
 *
 * @param recipeTitle names the meal for TalkBack; `null` when the recipe is not on this device.
 */
@Composable
fun MealRemoveButton(
    recipeTitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = recipeTitle ?: stringResource(R.string.meal_plan_recipe_unavailable)
    Box(
        modifier = modifier
            .size(SlotControlTouchTarget)
            .flatClickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(ChefAIIcons.Trash),
            contentDescription = stringResource(R.string.meal_plan_remove_meal, title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * An empty slot: "Add recipe" in the accent, with the slot name beneath when the day holds more
 * than one meal — the same title-then-meta rhythm a filled meal line has, so a gap reads as a slot.
 */
@Composable
fun AddRecipeToSlotLink(
    slotLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = SlotControlTouchTarget)
            .flatClickable(onClick = onClick, role = Role.Button)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.meal_plan_add_recipe),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.chefColors.accentText,
        )
        if (slotLabel != null) {
            Text(
                text = slotLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The hairline between two slots of the **same day**. Unlike every other rule in the system it is
 * inset — it starts at the meal column, past the date — so lunch and dinner read as one day with
 * two slots rather than two days that happen to share a date. Between days, use a full-bleed
 * [RowRule] as usual.
 *
 * @param start where the meal column begins, measured from the row's outer edge.
 * @param end the inset from the row's far edge; `0.dp` when the rule already sits inside the
 *   meal column, as in the week view.
 */
@Composable
fun SameDaySlotRule(
    start: Dp,
    modifier: Modifier = Modifier,
    end: Dp = 16.dp,
) {
    RowRule(modifier = modifier.padding(start = start, end = end))
}

private val SlotControlTouchTarget = 48.dp

@Preview(name = "Slot controls — light")
@Preview(name = "Slot controls — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SlotControlsPreview() {
    ChefAITheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AddRecipeToSlotLink(slotLabel = "Lunch", onClick = {}, modifier = Modifier.padding(start = 16.dp))
                MealRemoveButton(recipeTitle = "Carbonara", onClick = {})
            }
            SameDaySlotRule(start = 16.dp)
        }
    }
}
