package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanServingBasis

/**
 * The header's "Just me" / "Family" segmented control — `.seg` in the handoff's stylesheet.
 *
 * A local component rather than one in `core/ui/components/flat/`: screen 06 is the only place in
 * the 19-screen design that uses a segmented control, and ADR-005's rule is to stay in the feature
 * package until a second feature needs it. Promote it if a second one does.
 *
 * @param canSelectFamily `false` renders the Family segment at 45% opacity and stops it accepting
 *   taps — a user with no household has nothing to switch to. See ADR-015 Decision 5.
 */
@Composable
fun ServingBasisToggle(
    basis: MealPlanServingBasis,
    onBasisChange: (MealPlanServingBasis) -> Unit,
    modifier: Modifier = Modifier,
    canSelectFamily: Boolean = true,
) {
    val groupLabel = stringResource(R.string.meal_plans_basis_label)
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
        MealPlanServingBasis.entries.forEachIndexed { index, entry ->
            if (index > 0) {
                VerticalDivider(
                    thickness = MaterialTheme.chefColors.rowRuleWidth,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Segment(
                label = stringResource(entry.labelRes()),
                selected = entry == basis,
                enabled = entry != MealPlanServingBasis.FAMILY || canSelectFamily,
                onClick = { onBasisChange(entry) },
            )
        }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else DisabledAlpha)
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
                enabled = enabled,
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

private fun MealPlanServingBasis.labelRes(): Int = when (this) {
    MealPlanServingBasis.JUST_ME -> R.string.meal_plans_basis_just_me
    MealPlanServingBasis.FAMILY -> R.string.meal_plans_basis_family
}

/** 44dp, the system's minimum hit target — the CSS's 7px padding does not reach it. */
private val SegmentHeight = 44.dp

/** Fixed so the two segments are equal and the control does not resize as the selection moves. */
private val SegmentWidth = 62.dp

/** `.btn:disabled { opacity: 0.45 }`. */
private const val DisabledAlpha = 0.45f

@Preview(name = "Serving basis — light")
@Preview(name = "Serving basis — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ServingBasisTogglePreview() {
    ChefAITheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            ServingBasisToggle(
                basis = MealPlanServingBasis.JUST_ME,
                onBasisChange = {},
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            ServingBasisToggle(
                basis = MealPlanServingBasis.FAMILY,
                onBasisChange = {},
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            ServingBasisToggle(
                basis = MealPlanServingBasis.JUST_ME,
                onBasisChange = {},
                canSelectFamily = false,
            )
        }
    }
}
