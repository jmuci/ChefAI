package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The flat checkbox — an 18dp square with a 2dp rule, filled accent with a ground-colored tick
 * when checked. No ripple, no 48dp Material halo, no rounded corners.
 *
 * It appears in two situations, and the difference is what [onCheckedChange] is:
 *
 * ```
 * // 1. The checkbox is the control. Remember-me on Log in.
 * FlatCheckbox(checked = rememberMe, onCheckedChange = onRememberMeChange)
 *
 * // 2. The whole row toggles — recipe ingredients, shopping-list items, cooked meals.
 * //    Pass null so the checkbox does not steal the row's click or double up its semantics,
 * //    and give the row flatToggleable so the checked state reaches the semantics tree.
 * Row(Modifier.flatToggleable(checked = item.isChecked, onCheckedChange = onToggle)) {
 *     FlatCheckbox(checked = item.isChecked, onCheckedChange = null)
 *     …
 * }
 * ```
 *
 * Used on recipe detail (05), meal plan detail (16), shopping list (17) and log in (07).
 *
 * @param onCheckedChange `null` makes the checkbox a passive indicator — the enclosing row is then
 *   responsible for the click *and* for publishing the checked state, which is what
 *   [flatToggleable] does.
 */
@Composable
fun FlatCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // 18dp leaves no room for a pressed background, so the fill steps one along the ramp instead.
    val fill = if (pressed) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val tick = MaterialTheme.colorScheme.onPrimary
    val emptyBorder = if (pressed) {
        MaterialTheme.chefColors.neutral.s600
    } else {
        MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .size(BoxSize)
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .modernistFocusRing(interactionSource)
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Checkbox,
                            interactionSource = interactionSource,
                            indication = null,
                            onValueChange = onCheckedChange,
                        )
                } else {
                    Modifier
                },
            )
            .border(
                width = MaterialTheme.chefColors.sectionRuleWidth,
                color = if (checked) fill else emptyBorder,
            )
            .then(if (checked) Modifier.background(fill) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Tick(color = tick)
        }
    }
}

/**
 * The tick, drawn rather than iconified: Material's check is a hairline next to the design's
 * `stroke-width: 3` Lucide glyph, and at 11dp that reads as a smudge.
 */
@Composable
private fun Tick(color: Color) {
    Canvas(modifier = Modifier.size(TickSize)) {
        // The handoff's path, `M20 6 9 17l-5-5`, normalized out of its 24-unit viewBox.
        val path = Path().apply {
            moveTo(size.width * 0.167f, size.height * 0.500f)
            lineTo(size.width * 0.375f, size.height * 0.708f)
            lineTo(size.width * 0.833f, size.height * 0.250f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = TickStroke.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private val BoxSize = 18.dp
private val TickSize = 11.dp
private val TickStroke = 2.dp

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun FlatCheckboxPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Checked / unchecked")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlatCheckbox(checked = true, onCheckedChange = {})
            FlatCheckbox(checked = false, onCheckedChange = {})
        }
        PreviewStateLabel("Disabled — checked / unchecked")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlatCheckbox(checked = true, onCheckedChange = {}, enabled = false)
            FlatCheckbox(checked = false, onCheckedChange = {}, enabled = false)
        }
        PreviewStateLabel("Passive, inside a clickable row")
        RuledGroup(items = listOf("Salmon fillets" to true, "Soy sauce" to false)) { (name, done) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinHitTarget)
                    .flatToggleable(checked = done, onCheckedChange = {})
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlatCheckbox(checked = done, onCheckedChange = null)
                Text(text = name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
