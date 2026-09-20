package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The selectable chip — Modernist's `FilterChip`.
 *
 * Selection is carried by **fill and weight**, not by a check mark and not by a border that
 * thickens: selected is an accent block with a ground-colored ExtraBold label; unselected is a 2dp
 * rule around nothing with the label at body weight.
 *
 * ```
 * FlatChip(
 *     label = stringResource(restriction.labelRes),
 *     selected = restriction in uiState.dietaryRestrictions,
 *     onClick = { onAction(ToggleRestriction(restriction)) },
 * )
 * ```
 *
 * Multi-select (dietary preferences) and single-select (max prep time, variety) look identical —
 * the difference lives in the state the screen keeps, not in the chip. Set [singleSelect] when the
 * group is a one-of-many choice: the chip then reports itself as a selected/unselected radio
 * option rather than a checked/unchecked box, and the group should carry
 * `Modifier.selectableGroup()`.
 *
 * Used on wizard preferences (14), wizard advanced (15) and the recipe list filters (04).
 *
 * @param enabled `false` renders the chip at 45% opacity and stops accepting taps — the
 *   `collectionTooSmall` case on screen 14.
 * @param singleSelect `true` when this chip belongs to a radio-style group. Affects semantics
 *   only — nothing about how the chip is drawn.
 */
@Composable
fun FlatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleSelect: Boolean = false,
) {
    val selectedFill = MaterialTheme.colorScheme.primary
    val pressedTint = if (selected) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.chefColors.neutral.s200
    }
    // `toggleable`/`selectable` rather than `clickable`: in this design selection is carried by
    // fill and weight alone, so if the state does not reach the semantics tree a screen-reader
    // user has no way at all to tell a selected chip from an unselected one.
    val selectionModifier = if (singleSelect) {
        Modifier.flatSelectable(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            pressedTint = pressedTint,
        )
    } else {
        Modifier.flatToggleable(
            checked = selected,
            onCheckedChange = { onClick() },
            enabled = enabled,
            pressedTint = pressedTint,
        )
    }

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .heightIn(min = MinHitTarget)
            // Fill before the selection modifier so the pressed tint replaces it; border after it
            // so the tint does not paint over the 2dp rule the unselected chip is made of.
            .then(if (selected) Modifier.background(selectedFill) else Modifier)
            .then(selectionModifier)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(
                        width = MaterialTheme.chefColors.sectionRuleWidth,
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
            )
            .padding(horizontal = ChipHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            // 13px in both states; only the weight and the color move. The design's selected chip
            // is 13/800 and its unselected one is 13/400 — same role, different weight.
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
    }
}

/** `horizontal padding 14dp`, per the handoff's `FlatChip` spec. */
private val ChipHorizontalPadding = 14.dp

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun FlatChipPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Selected / unselected / disabled")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatChip(label = "Vegetarian", selected = true, onClick = {})
            FlatChip(label = "Vegan", selected = false, onClick = {})
            FlatChip(label = "Keto", selected = false, onClick = {}, enabled = false)
        }
        PreviewStateLabel("Selected + disabled")
        FlatChip(label = "Low Carb", selected = true, onClick = {}, enabled = false)
        PreviewStateLabel("Wrapping group — wizard preferences")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "No restrictions" to false,
                "Vegan" to false,
                "Vegetarian" to true,
                "Low Carb" to true,
                "Gluten-Free" to false,
                "Dairy-Free" to false,
            ).forEach { (text, isSelected) ->
                FlatChip(label = text, selected = isSelected, onClick = {})
            }
        }
    }
}
