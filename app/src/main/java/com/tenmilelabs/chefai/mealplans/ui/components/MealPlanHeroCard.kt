package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.mealplans.domain.summary.MealPlanSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * One plan in the plans list: a photo from one of its recipes, a title, a few of the recipes, and
 * when it runs.
 *
 * @param onScheduleClick the calendar button beside the schedule line. A no-op at the call site for
 *   now — picking a date for a plan lands in a follow-up.
 */
@Composable
fun MealPlanHeroCard(
    summary: MealPlanSummary,
    today: LocalDate,
    onClick: () -> Unit,
    onScheduleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaterialTheme.chefColors.panelRadius))
            .background(MaterialTheme.chefColors.panelSurface)
            .flatClickable(onClick = onClick),
    ) {
        Hero(recipe = summary.heroRecipe)

        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 8.dp),
        ) {
            Text(
                text = summary.displayTitle(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )

            if (summary.recipeTitles.isNotEmpty()) {
                Text(
                    text = summary.recipeTitles.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.meal_plans_card_meta,
                            pluralStringResource(
                                R.plurals.meal_plans_card_days,
                                summary.dayCount,
                                summary.dayCount,
                            ),
                            pluralStringResource(
                                R.plurals.meal_plans_card_recipes,
                                summary.recipeCount,
                                summary.recipeCount,
                            ),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = summary.scheduleLabel(today),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.chefColors.accentText,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .flatClickable(onClick = onScheduleClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(ChefAIIcons.Calendar),
                        contentDescription = stringResource(R.string.meal_plans_card_schedule),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Hero(recipe: RecipePreview?, modifier: Modifier = Modifier) {
    val model = recipe?.let { recipeImageModel(it.localImagePath, it.imageUrlThumbnail) }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(true).build(),
        placeholder = painterResource(R.drawable.ic_img_placeholder),
        error = painterResource(R.drawable.ic_img_error),
        fallback = painterResource(R.drawable.ic_img_placeholder),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** The user's own title, else one built from the food in the plan, else a plain fallback. */
@Composable
private fun MealPlanSummary.displayTitle(): String = when {
    customName != null -> customName
    dominantTag != null -> stringResource(R.string.meal_plans_generated_title, dominantTag)
    else -> stringResource(R.string.meal_plans_untitled)
}

/**
 * When the plan runs — or, for one whose last day has passed, when it was last cooked, since a
 * schedule that is over tells the reader nothing. A past plan nobody cooked from keeps its range.
 */
@Composable
private fun MealPlanSummary.scheduleLabel(today: LocalDate): String {
    val lastCooked = lastCookedOn
    return if (isPast(today) && lastCooked != null) {
        stringResource(R.string.meal_plans_card_last_cooked, formatDay(lastCooked))
    } else {
        stringResource(R.string.meal_plans_card_scheduled, formatRange(startDate, endDate))
    }
}

private fun formatDay(date: LocalDate): String =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(date)

/** "Aug 11 – 17" within a month, "Aug 28 – Sep 3" across one. */
private fun formatRange(start: LocalDate, end: LocalDate): String {
    if (start == end) return formatDay(start)
    val tail = if (start.month == end.month) end.dayOfMonth.toString() else formatDay(end)
    return "${formatDay(start)} – $tail"
}

private fun previewSummary(
    customName: String?,
    startDate: LocalDate,
    lastCookedOn: LocalDate? = null,
) = MealPlanSummary(
    planId = UUID.randomUUID(),
    customName = customName,
    dominantTag = "Italian",
    heroRecipe = null,
    recipeTitles = listOf("Spaghetti Carbonara", "Margherita Pizza", "Mushroom Risotto"),
    recipeCount = 9,
    dayCount = 5,
    startDate = startDate,
    endDate = startDate.plusDays(4),
    lastCookedOn = lastCookedOn,
    isShared = false,
)

@Preview(name = "Plan hero card — light")
@Preview(name = "Plan hero card — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MealPlanHeroCardPreview() {
    val today = LocalDate.of(2026, 8, 12)
    ChefAITheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.chefColors.panelGround)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MealPlanHeroCard(
                summary = previewSummary(null, today),
                today = today,
                onClick = {},
                onScheduleClick = {},
            )
            MealPlanHeroCard(
                summary = previewSummary("Birthday week", today.minusDays(30), today.minusDays(27)),
                today = today,
                onClick = {},
                onScheduleClick = {},
            )
        }
    }
}
