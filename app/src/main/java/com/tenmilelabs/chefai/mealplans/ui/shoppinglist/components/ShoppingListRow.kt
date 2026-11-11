package com.tenmilelabs.chefai.mealplans.ui.shoppinglist.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatCheckbox
import com.tenmilelabs.chefai.core.ui.components.flat.flatToggleableWithLongClick
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/** What a row asks of its screen. One lambda, so the row's signature doesn't grow with each gesture. */
sealed interface ShoppingListRowAction {
    data object Toggle : ShoppingListRowAction
    data object LongPress : ShoppingListRowAction
    data object Rename : ShoppingListRowAction
    data object Remove : ShoppingListRowAction
}

/** How much of its normal opacity a picked-up row keeps — "struck through at 50% ink". */
private const val CHECKED_ALPHA = 0.5f
private const val STRIKE_ANIM_MS = 240

/**
 * One line of the shopping list: an 18dp checkbox, the ingredient, a right-aligned quantity.
 *
 * A long press reveals a pencil and an X on the right, to rename or remove the line; the screen
 * decides when to hide them again ([actionsVisible]).
 *
 * Ticking sweeps a strike-through across the name left-to-right and fades the row back, rather
 * than snapping to `TextDecoration.LineThrough` — the sweep is what makes a tick feel like
 * crossing something off a paper list.
 */
@Composable
fun ShoppingListRow(
    name: String,
    quantityLabel: String?,
    isChecked: Boolean,
    onAction: (ShoppingListRowAction) -> Unit,
    isApproximate: Boolean = false,
    checkedByName: String? = null,
    actionsVisible: Boolean = false,
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
            MaterialTheme.colorScheme.onBackground
        },
        animationSpec = tween(STRIKE_ANIM_MS),
        label = "rowContentColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .flatToggleableWithLongClick(
                checked = isChecked,
                onCheckedChange = { onAction(ShoppingListRowAction.Toggle) },
                onLongClick = { onAction(ShoppingListRowAction.LongPress) },
                onLongClickLabel = stringResource(R.string.shopping_list_show_actions, name),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // onCheckedChange = null: the row owns the click via flatToggleableWithLongClick, so the box must not
        // register a second, competing semantics node.
        FlatCheckbox(checked = isChecked, onCheckedChange = null)

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
            if (checkedByName != null) {
                Text(
                    text = stringResource(R.string.shopping_list_checked_by, checkedByName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (spokenAmount != null) {
                    Modifier.semantics { contentDescription = spokenAmount }
                } else {
                    Modifier
                },
            )
        }

        if (actionsVisible) {
            IconButton(
                onClick = { onAction(ShoppingListRowAction.Rename) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(ChefAIIcons.Pencil),
                    contentDescription = stringResource(R.string.shopping_list_edit_item, name),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { onAction(ShoppingListRowAction.Remove) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(ChefAIIcons.X),
                    contentDescription = stringResource(R.string.shopping_list_remove_item, name),
                    modifier = Modifier.size(18.dp),
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
            onAction = {},
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
            onAction = {},
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
            onAction = {},
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
            onAction = {},
        )
    }
}

@Preview(name = "Approximate weight — Light", showBackground = true)
@Composable
private fun ShoppingListRowApproximatePreview() {
    ChefAITheme(darkTheme = false) {
        ShoppingListRow(
            name = "Beef mince",
            quantityLabel = "900 g",
            isApproximate = true,
            isChecked = false,
            onAction = {},
        )
    }
}

@Preview(name = "Picked up, checked by — Light", showBackground = true)
@Composable
private fun ShoppingListRowCheckedByPreview() {
    ChefAITheme(darkTheme = false) {
        ShoppingListRow(
            name = "Chicken breast",
            quantityLabel = "500 g",
            isChecked = true,
            checkedByName = "Alex",
            onAction = {},
        )
    }
}

@Preview(name = "Picked up, checked by — Dark", showBackground = true)
@Composable
private fun ShoppingListRowCheckedByDarkPreview() {
    ChefAITheme(darkTheme = true) {
        ShoppingListRow(
            name = "Chicken breast",
            quantityLabel = "500 g",
            isChecked = true,
            checkedByName = "Alex",
            onAction = {},
        )
    }
}

@Preview(name = "Actions revealed — Light", showBackground = true)
@Composable
private fun ShoppingListRowActionsLightPreview() {
    ChefAITheme(darkTheme = false) {
        ShoppingListRow(
            name = "Extra-virgin olive oil",
            quantityLabel = "2 tbsp",
            isChecked = false,
            actionsVisible = true,
            onAction = {},
        )
    }
}

@Preview(name = "Actions revealed — Dark", showBackground = true)
@Composable
private fun ShoppingListRowActionsDarkPreview() {
    ChefAITheme(darkTheme = true) {
        ShoppingListRow(
            name = "Extra-virgin olive oil",
            quantityLabel = "2 tbsp",
            isChecked = false,
            actionsVisible = true,
            onAction = {},
        )
    }
}
