package com.tenmilelabs.chefai.mealplans.ui.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.components.flat.flatSelectable
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * A full-width, 52dp selectable row — "Dinners only", "My collection only". Selection is carried
 * by fill and color, the same language as `FlatChip`, just stacked instead of inline and with the
 * label flush left like a `FlatBlockButton`.
 */
@Composable
fun WizardOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val pressedTint = if (selected) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.chefColors.neutral.s200
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .alpha(if (enabled) 1f else DisabledAlpha)
            // Fill before the selection modifier so the pressed tint replaces it; border after it
            // so the tint does not paint over the 2dp rule the unselected row is made of.
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
            .flatSelectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                pressedTint = pressedTint,
            )
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
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
    }
}

private val RowHeight = 52.dp

/** `.btn:disabled { opacity: 0.45 }` — the `collectionTooSmall` case on "My collection only". */
private const val DisabledAlpha = 0.45f
