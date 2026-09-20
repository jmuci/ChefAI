package com.tenmilelabs.chefai.mealplans.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.flat.FlatCheckbox
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.util.UUID

/** How much of its normal opacity a cooked row's title keeps — "struck through at 50% ink". */
private const val COOKED_ALPHA = 0.5f

/**
 * One day of a meal plan: an 18dp cooked checkbox, a day/date column, then the meal itself.
 *
 * Screen 16 in the handoff draws the week as one ordered list rather than a "to cook" pile plus a
 * "cooked" pile — ticking a meal strikes it through in place instead of moving the row, so tapping
 * the checkbox re-renders the segment bar without the list reflowing under the user's thumb.
 *
 * @param recipe the recipe filling the slot, or `null` when it is not on this device.
 * @param dayAbbreviation "MON" — three letters, uppercase.
 * @param dayNumber the day-of-month, e.g. "11".
 * @param isToday tints the day column accent, per the handoff.
 * @param isCooked drives the struck-through, dimmed treatment and the checkbox's state. A cooked
 *   row shows only its title — the meta line is what is left to decide before cooking, so it drops
 *   once the meal is done.
 * @param slotLabel "Lunch"/"Dinner", or `null` for a plan with a single meal per day, where the
 *   label would be the same on every row and only adds noise.
 */
@Composable
fun MealPlanMealRow(
    recipe: RecipePreview?,
    dayAbbreviation: String,
    dayNumber: String,
    isToday: Boolean,
    isCooked: Boolean,
    onClick: () -> Unit,
    onToggleCooked: () -> Unit,
    modifier: Modifier = Modifier,
    slotLabel: String? = null,
) {
    val dayColor = if (isToday) {
        MaterialTheme.chefColors.accentText
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlatCheckbox(
            checked = isCooked,
            onCheckedChange = { onToggleCooked() },
        )

        Column(
            modifier = Modifier.width(40.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = dayAbbreviation,
                style = MaterialTheme.typography.labelSmall,
                color = dayColor,
            )
            Text(
                text = dayNumber,
                style = MaterialTheme.typography.titleLarge,
                color = dayColor,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .flatClickable(onClick = onClick, role = Role.Button)
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .alpha(if (isCooked) COOKED_ALPHA else 1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (slotLabel != null) {
                Text(
                    text = slotLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.chefColors.accentText,
                )
            }

            Text(
                text = recipe?.title ?: stringResource(R.string.meal_plan_recipe_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                textDecoration = if (isCooked) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!isCooked && recipe != null) {
                val meta = recipe.metaLine()
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "25 min · Serves 4", dropping either half when the recipe does not carry it. */
private fun RecipePreview.metaLine(): String {
    val totalMinutes = prepTimeMinutes + cookTimeMinutes
    return listOfNotNull(
        totalMinutes.takeIf { it > 0 }?.let { "${it}m" },
        servings.takeIf { it > 0 }?.let { "Serves $it" },
    ).joinToString(" · ")
}

private fun previewRecipe(title: String) = RecipePreview(
    uuid = UUID.randomUUID(),
    title = title,
    description = "",
    imageUrlThumbnail = "",
    prepTimeMinutes = 10,
    cookTimeMinutes = 15,
    servings = 2,
    creatorId = UUID.randomUUID(),
    tags = emptyList(),
    labels = emptyList(),
)

@Preview(name = "To cook — Light", showBackground = true)
@Composable
private fun MealPlanMealRowToCookPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanMealRow(
            recipe = previewRecipe("Lemon Dill Salmon"),
            dayAbbreviation = "THU",
            dayNumber = "14",
            isToday = false,
            isCooked = false,
            onClick = {},
            onToggleCooked = {},
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(name = "Today — Light", showBackground = true)
@Composable
private fun MealPlanMealRowTodayPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanMealRow(
            recipe = previewRecipe("Grilled Salmon Teriyaki"),
            dayAbbreviation = "TUE",
            dayNumber = "12",
            isToday = true,
            isCooked = false,
            slotLabel = MealSlot.DINNER.label,
            onClick = {},
            onToggleCooked = {},
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(name = "Cooked — Light", showBackground = true)
@Composable
private fun MealPlanMealRowCookedPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanMealRow(
            recipe = previewRecipe("Classic Spaghetti Carbonara"),
            dayAbbreviation = "MON",
            dayNumber = "11",
            isToday = false,
            isCooked = true,
            onClick = {},
            onToggleCooked = {},
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(name = "Cooked — Dark", showBackground = true)
@Composable
private fun MealPlanMealRowCookedDarkPreview() {
    ChefAITheme(darkTheme = true) {
        MealPlanMealRow(
            recipe = previewRecipe("Miso Aubergine Rice Bowl"),
            dayAbbreviation = "FRI",
            dayNumber = "15",
            isToday = false,
            isCooked = true,
            onClick = {},
            onToggleCooked = {},
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(name = "Recipe missing — Light", showBackground = true)
@Composable
private fun MealPlanMealRowMissingPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanMealRow(
            recipe = null,
            dayAbbreviation = "WED",
            dayNumber = "13",
            isToday = false,
            isCooked = false,
            onClick = {},
            onToggleCooked = {},
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
