package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.domain.model.Tag
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.week.PlannedWeekMeal
import com.tenmilelabs.chefai.mealplans.domain.week.WeekDay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/**
 * One day of the week: a fixed date column, then whatever is planned on it.
 *
 * A day can hold both a lunch and a dinner ([com.tenmilelabs.chefai.mealplans.domain.model.MealType.DINNER_AND_LUNCH]),
 * so the meals **stack** beside a single date column rather than the row being one meal wide.
 * Dropping the second to keep a literal one-line-per-day reading would hide planned data.
 */
@Composable
fun WeekDayRow(
    day: WeekDay,
    isToday: Boolean,
    onMealClick: (PlannedWeekMeal) -> Unit,
    onAddMeal: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DateColumn(date = day.date, isToday = isToday)
        Spacer(Modifier.width(8.dp))
        if (day.meals.isEmpty()) {
            AddMealLink(onClick = { onAddMeal(day.date) })
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                day.meals.forEach { meal ->
                    MealLine(
                        meal = meal,
                        showSlotLabel = day.meals.size > 1,
                        onClick = { onMealClick(meal) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DateColumn(date: LocalDate, isToday: Boolean) {
    // Accent-700, not the brand accent: this is an 11px label, below the size where accent-600
    // clears AA on the ground.
    val dayColor = if (isToday) {
        MaterialTheme.chefColors.accentText
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.width(DateColumnWidth)) {
        Text(
            text = date.dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = dayColor,
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = if (isToday) {
                MaterialTheme.chefColors.accentText
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
    }
}

@Composable
private fun AddMealLink(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.meal_plans_add_meal),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.chefColors.accentText,
        modifier = Modifier
            .heightIn(min = LinkHitTarget)
            .flatClickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun MealLine(
    meal: PlannedWeekMeal,
    showSlotLabel: Boolean,
    onClick: () -> Unit,
) {
    val recipe = meal.recipe
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .flatClickable(onClick = onClick),
    ) {
        Text(
            text = recipe?.title ?: stringResource(R.string.meal_plan_recipe_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.ExtraBold,
            // A cooked meal drops to muted ink, the same demotion the detail screen gives its
            // "Cooked this week" section. Not a strikethrough: it still happened.
            color = if (meal.isCooked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
        val meta = buildMeta(meal = meal, recipe = recipe, showSlotLabel = showSlotLabel)
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "30m · pasta", with the slot prefixed only when a day holds both meals. */
@Composable
private fun buildMeta(
    meal: PlannedWeekMeal,
    recipe: RecipePreview?,
    showSlotLabel: Boolean,
): String {
    val parts = buildList {
        if (showSlotLabel) add(meal.slot.label)
        if (recipe != null) {
            val minutes = recipe.prepTimeMinutes + recipe.cookTimeMinutes
            if (minutes > 0) add("${minutes}m")
            val category = recipe.tags.firstOrNull()?.displayName
                ?: recipe.labels.firstOrNull()?.displayName
            if (category != null) add(category.lowercase(Locale.getDefault()))
        }
    }
    return parts.joinToString(" · ")
}

/** The design's 44px date column. */
private val DateColumnWidth = 44.dp
private val RowMinHeight = 56.dp
private val LinkHitTarget = 44.dp

@Preview(name = "Week day rows — light")
@Preview(name = "Week day rows — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeekDayRowPreview() {
    ChefAITheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            WeekDayRow(
                day = WeekDay(
                    date = LocalDate.of(2026, 8, 11),
                    meals = listOf(previewMeal("Classic Spaghetti Carbonara", "pasta", 30)),
                ),
                isToday = false,
                onMealClick = {},
                onAddMeal = {},
            )
            WeekDayRow(
                day = WeekDay(
                    date = LocalDate.of(2026, 8, 12),
                    meals = listOf(previewMeal("Grilled Salmon Teriyaki", "seafood", 25)),
                ),
                isToday = true,
                onMealClick = {},
                onAddMeal = {},
            )
            WeekDayRow(
                day = WeekDay(date = LocalDate.of(2026, 8, 13), meals = emptyList()),
                isToday = false,
                onMealClick = {},
                onAddMeal = {},
            )
            WeekDayRow(
                day = WeekDay(
                    date = LocalDate.of(2026, 8, 14),
                    meals = listOf(
                        previewMeal("Avocado Toast with Egg", "breakfast", 10, MealSlot.LUNCH),
                        previewMeal("Hearty Beef Chilli", "comfort food", 80, cooked = true),
                    ),
                ),
                isToday = false,
                onMealClick = {},
                onAddMeal = {},
            )
        }
    }
}

private fun previewMeal(
    title: String,
    tag: String,
    minutes: Int,
    slot: MealSlot = MealSlot.DINNER,
    cooked: Boolean = false,
): PlannedWeekMeal {
    val recipeId = UUID.randomUUID()
    return PlannedWeekMeal(
        planId = UUID.randomUUID(),
        dayId = UUID.randomUUID(),
        slot = slot,
        recipeId = recipeId,
        recipe = RecipePreview(
            uuid = recipeId,
            title = title,
            description = "",
            imageUrlThumbnail = "",
            prepTimeMinutes = minutes,
            cookTimeMinutes = 0,
            servings = 4,
            creatorId = UUID.randomUUID(),
            tags = listOf(Tag(UUID.randomUUID(), tag)),
            labels = emptyList(),
        ),
        isCooked = cooked,
    )
}
