package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.flatSelectable
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.mealplans.ui.MealPlansViewMode

/**
 * The header's "Week" / "Plans" segmented control: the calendar view or the list of plans.
 *
 * Kept in the feature package, as the serving-basis control it replaces was — this is still the
 * only segmented control in the design (ADR-005: promote on a second user).
 */
@Composable
fun MealPlansViewToggle(
    mode: MealPlansViewMode,
    onModeChange: (MealPlansViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupLabel = stringResource(R.string.meal_plans_view_label)
    Row(
        modifier = modifier
            .semantics { contentDescription = groupLabel }
            .border(
                width = MaterialTheme.chefColors.rowRuleWidth,
                color = MaterialTheme.colorScheme.outline,
            )
            .heightIn(min = SegmentHeight)
            // VerticalDivider is fillMaxHeight(): without an intrinsic height the Row takes every
            // pixel its parent allows, and in the Meal Plans top bar that is the whole screen.
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MealPlansViewMode.entries.forEachIndexed { index, entry ->
            if (index > 0) {
                VerticalDivider(
                    thickness = MaterialTheme.chefColors.rowRuleWidth,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Segment(
                label = stringResource(entry.labelRes()),
                selected = entry == mode,
                onClick = { onModeChange(entry) },
            )
        }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(SegmentWidth)
            .heightIn(min = SegmentHeight)
            // Fill first so the pressed tint replaces it rather than compositing over it, the same
            // ordering FlatChip uses.
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier
                },
            )
            .flatSelectable(
                selected = selected,
                onClick = onClick,
                pressedTint = if (selected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.chefColors.neutral.s200
                },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            // `onPrimary` is the ground in this system, not white — the selected segment reads as
            // a knockout rather than as a colored label.
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            textAlign = TextAlign.Center,
        )
    }
}

private fun MealPlansViewMode.labelRes(): Int = when (this) {
    MealPlansViewMode.WEEK -> R.string.meal_plans_view_week
    MealPlansViewMode.PLANS -> R.string.meal_plans_view_plans
}

/** 44dp, the system's minimum hit target. */
private val SegmentHeight = 44.dp

/** Fixed so the two segments are equal and the control does not resize as the selection moves. */
private val SegmentWidth = 62.dp

@Preview(name = "View toggle — light")
@Preview(name = "View toggle — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MealPlansViewTogglePreview() {
    ChefAITheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            MealPlansViewToggle(mode = MealPlansViewMode.WEEK, onModeChange = {})
            Spacer(Modifier.height(12.dp))
            MealPlansViewToggle(mode = MealPlansViewMode.PLANS, onModeChange = {})
        }
    }
}
