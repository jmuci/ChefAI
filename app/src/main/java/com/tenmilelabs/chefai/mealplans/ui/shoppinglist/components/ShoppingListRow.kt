package com.tenmilelabs.chefai.mealplans.ui.shoppinglist.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/** How much of its normal opacity a picked-up row keeps. Matches MealPlanMealRow's COOKED_ALPHA. */
private const val CHECKED_ALPHA = 0.45f
private const val STRIKE_ANIM_MS = 240

/**
 * One line of the shopping list: checkbox, ingredient, quantity.
 *
 * Ticking sweeps a strike-through across the text left-to-right and fades the row back, rather than
 * snapping to `TextDecoration.LineThrough` — the sweep is what makes a tick feel like crossing
 * something off a paper list.
 */
@Composable
fun ShoppingListRow(
    name: String,
    quantityLabel: String?,
    isChecked: Boolean,
    onToggle: () -> Unit,
    isApproximate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Held as State, not unwrapped with `by`: nothing else in the composition reads .value, so
    // each animation frame redraws the strike-through without recomposing the row.
    val strike = animateFloatAsState(
        targetValue = if (isChecked) 1f else 0f,
        animationSpec = tween(STRIKE_ANIM_MS, easing = FastOutSlowInEasing),
        label = "strikeThrough",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isChecked) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(STRIKE_ANIM_MS),
        label = "rowContentColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = isChecked,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            )
            .semantics {
                stateDescription = if (isChecked) "Picked up" else "Still to buy"
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // onCheckedChange = null: the row owns the click via toggleable, so the box must not
        // register a second, competing semantics node.
        Checkbox(checked = isChecked, onCheckedChange = null)

        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = lerp(1f, CHECKED_ALPHA, strike.value) },
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sweepingStrikeThrough(contentColor) { strike.value },
            )
            if (quantityLabel != null) {
                // "≈" marks a total that rests on an assumed density, exactly as the recipe
                // screen marks one; a screen reader gets the word, which it would skip as a glyph.
                val spokenAmount = if (isApproximate) {
                    stringResource(R.string.ingredient_amount_approximate, quantityLabel)
                } else {
                    null
                }
                Text(
                    text = if (isApproximate) "≈ $quantityLabel" else quantityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (spokenAmount != null) {
                        Modifier.semantics { contentDescription = spokenAmount }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/** Draws a line through the content, sweeping left-to-right as [progress] runs 0 to 1. */
private fun Modifier.sweepingStrikeThrough(
    color: Color,
    progress: () -> Float,
): Modifier = drawWithContent {
    drawContent()
    val fraction = progress()
    if (fraction > 0f) {
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width * fraction, y),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Preview(name = "To buy — Light", showBackground = true)
@Composable
private fun ShoppingListRowToBuyPreview() {
    ChefAITheme(darkTheme = false) {
        ShoppingListRow(
            name = "Extra-virgin olive oil",
            quantityLabel = "2 tbsp",
            isChecked = false,
            onToggle = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Picked up — Light", showBackground = true)
@Composable
private fun ShoppingListRowCheckedPreview() {
    ChefAITheme(darkTheme = false) {
        ShoppingListRow(
            name = "Chicken breast",
            quantityLabel = "500 g",
            isChecked = true,
            onToggle = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "To buy — Dark", showBackground = true)
@Composable
private fun ShoppingListRowToBuyDarkPreview() {
    ChefAITheme(darkTheme = true) {
        ShoppingListRow(
            name = "Baby spinach",
            quantityLabel = null,
            isChecked = false,
            onToggle = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Picked up — Dark", showBackground = true)
@Composable
private fun ShoppingListRowCheckedDarkPreview() {
    ChefAITheme(darkTheme = true) {
        ShoppingListRow(
            name = "Greek yogurt",
            quantityLabel = "500 g",
            isChecked = true,
            onToggle = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
