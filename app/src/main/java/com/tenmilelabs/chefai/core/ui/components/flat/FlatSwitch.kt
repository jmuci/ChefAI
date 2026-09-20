package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The flat switch — 48×28dp, square, no thumb shadow and no track that changes shape.
 *
 * On: a 2dp accent border filled with accent, knob in the ground color, flush right.
 * Off: a 2dp rule around nothing, knob in the rule color, flush left.
 *
 * ```
 * FlatSwitch(checked = uiState.batchCooking, onCheckedChange = { onAction(SetBatchCooking(it)) })
 * ```
 *
 * The **visual** is 48×28, but the composable reserves 44dp of height so the switch alone clears
 * the minimum hit target; the extra height is centered and paints nothing. In a title + subtitle
 * row (the wizard's `ToggleOptionRow`) that costs nothing, because the row is already taller.
 *
 * Used on wizard advanced (15).
 */
@Composable
fun FlatSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // The switch has no room for a pressed *background* — it is 48dp wide and the track already
    // fills it — so the pressed feedback is the track itself stepping one along its ramp.
    val trackColor = when {
        checked && pressed -> MaterialTheme.colorScheme.secondary
        checked -> MaterialTheme.colorScheme.primary
        pressed -> MaterialTheme.chefColors.neutral.s600
        else -> MaterialTheme.colorScheme.outline
    }
    val knobColor = if (checked) MaterialTheme.colorScheme.onPrimary else trackColor
    // The knob slides between the two insets rather than snapping: a flat system still moves.
    val knobOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - TrackInset - KnobSize else TrackInset,
        animationSpec = tween(durationMillis = 120),
        label = "flat_switch_knob",
    )

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .width(TrackWidth)
            .heightIn(min = MinHitTarget)
            .modernistFocusRing(interactionSource)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = TrackWidth, height = TrackHeight)
                .border(width = TrackBorder, color = trackColor)
                .then(if (checked) Modifier.background(trackColor) else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset)
                    .size(KnobSize)
                    .background(knobColor),
            )
        }
    }
}

/** 48×28dp with a 2dp border and a 2dp inset — which is exactly what leaves room for a 20dp knob. */
private val TrackWidth = 48.dp
private val TrackHeight = 28.dp
private val TrackBorder = 2.dp
private val TrackInset = TrackBorder + 2.dp
private val KnobSize = 20.dp

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun FlatSwitchPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("On / off")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlatSwitch(checked = true, onCheckedChange = {})
            FlatSwitch(checked = false, onCheckedChange = {})
        }
        PreviewStateLabel("Disabled — on / off")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlatSwitch(checked = true, onCheckedChange = {}, enabled = false)
            FlatSwitch(checked = false, onCheckedChange = {}, enabled = false)
        }
        PreviewStateLabel("In a toggle row")
        Row(
            modifier = Modifier.heightIn(min = MinHitTarget).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Batch cook & freeze",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(200.dp),
            )
            FlatSwitch(checked = true, onCheckedChange = {})
        }
    }
}
