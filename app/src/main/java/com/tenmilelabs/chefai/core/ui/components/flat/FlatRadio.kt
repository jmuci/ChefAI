package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The flat radio dot — 18dp, accent border, accent fill, and a 4dp ground-colored ring bitten out
 * of the middle (`box-shadow: inset 0 0 0 4px var(--color-bg)`).
 *
 * A dot is round for the same reason the avatar is: it is a dot, not a container. The zero-radius
 * rule is about boxes.
 *
 * Material's `RadioButton` can do none of this — not the inset ring, not the square-system border
 * weight, and it brings a ripple with a rounded bound.
 *
 * As with [FlatCheckbox], pass `onClick = null` when the enclosing row is the control, which is
 * the usual case — a radio row in this design is 44dp tall with a label and an example line:
 *
 * ```
 * Column(Modifier.selectableGroup()) {
 *     options.forEach { option ->
 *         Row(
 *             Modifier
 *                 .heightIn(min = 44.dp)
 *                 .flatSelectable(selected = option == current, onClick = { onSelect(option) }),
 *         ) {
 *             FlatRadio(selected = option == current, onClick = null)
 *             …
 *         }
 *     }
 * }
 * ```
 *
 * Used on Settings (10).
 *
 * @param onClick `null` makes the dot a passive indicator; the enclosing row then owns the click
 *   *and* publishes the selected state, which is what [flatSelectable] does.
 */
@Composable
fun FlatRadio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // As with FlatCheckbox: an 18dp dot steps its own colors rather than taking a pressed fill.
    val accent = if (pressed) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val emptyBorder = if (pressed) {
        MaterialTheme.chefColors.neutral.s600
    } else {
        MaterialTheme.colorScheme.outline
    }
    val borderWidth = MaterialTheme.chefColors.sectionRuleWidth

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .size(DotSize)
            .then(
                if (onClick != null) {
                    Modifier
                        .modernistFocusRing(interactionSource)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            )
            .border(
                width = borderWidth,
                color = if (selected) accent else emptyBorder,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            // Border, then the ground-colored ring, then the accent core — the CSS inset shadow
            // read outside-in.
            Box(
                modifier = Modifier
                    .size(DotSize - borderWidth * 2)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
                    .padding(RingInset)
                    .background(accent, CircleShape),
            )
        }
    }
}

private val DotSize = 18.dp

/** `inset 0 0 0 4px` — the ground ring between the accent border and the accent core. */
private val RingInset = 4.dp

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun FlatRadioPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Selected / unselected / disabled")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlatRadio(selected = true, onClick = {})
            FlatRadio(selected = false, onClick = {})
            FlatRadio(selected = false, onClick = {}, enabled = false)
        }
        PreviewStateLabel("Settings group — label goes 800 when selected")
        Column(Modifier.selectableGroup()) {
            RuledGroup(
                items = listOf(
                    Triple("As written", "1 cup flour, 2 tbsp butter", false),
                    Triple("Metric", "125 g flour, 30 g butter", true),
                    Triple("Imperial", "4.4 oz flour, 1 oz butter", false),
                ),
            ) { (label, example, isSelected) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinHitTarget)
                        .flatSelectable(selected = isSelected, onClick = {})
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlatRadio(selected = isSelected, onClick = null)
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                        )
                        Text(
                            text = example,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
