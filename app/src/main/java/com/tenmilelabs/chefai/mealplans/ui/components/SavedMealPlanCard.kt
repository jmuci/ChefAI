package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.summary.SavedMealPlanSummary
import java.util.UUID

/**
 * One saved plan in the "Saved" tab: photo, title, a few recipes, and "N days · Dinners".
 *
 * Tapping the card and the calendar button both mean "repeat this plan"; the trash button deletes
 * it. The caller confirms before deleting.
 */
@Composable
fun SavedMealPlanCard(
    summary: SavedMealPlanSummary,
    onRepeatClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaterialTheme.chefColors.panelRadius))
            .background(MaterialTheme.chefColors.panelSurface)
            .flatClickable(onClick = onRepeatClick),
    ) {
        Hero(model = summary.heroImageModel)

        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 8.dp)) {
            Text(
                text = summary.title,
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
                Text(
                    text = stringResource(
                        R.string.saved_plan_card_meta,
                        pluralStringResource(R.plurals.saved_plan_days, summary.dayCount, summary.dayCount),
                        stringResource(summary.mealType.labelRes()),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.chefColors.accentText,
                    modifier = Modifier.weight(1f),
                )
                CardIconButton(
                    iconRes = ChefAIIcons.Calendar,
                    contentDescription = stringResource(R.string.saved_plan_repeat),
                    onClick = onRepeatClick,
                    testTag = "SavedPlanCardRepeat",
                )
                CardIconButton(
                    iconRes = ChefAIIcons.Trash,
                    contentDescription = stringResource(R.string.saved_plan_delete),
                    onClick = onDeleteClick,
                    testTag = "SavedPlanCardDelete",
                )
            }
        }
    }
}

@Composable
private fun CardIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .flatClickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun MealType.labelRes(): Int = when (this) {
    MealType.DINNER -> R.string.saved_plan_meal_type_dinners
    MealType.DINNER_AND_LUNCH -> R.string.saved_plan_meal_type_dinners_and_lunches
}

@Preview(name = "Saved plan card — light")
@Preview(name = "Saved plan card — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SavedMealPlanCardPreview() {
    ChefAITheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.chefColors.panelGround)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SavedMealPlanCard(
                summary = SavedMealPlanSummary(
                    id = UUID.randomUUID(),
                    title = "Easy weeknights",
                    heroImageModel = null,
                    recipeTitles = listOf("Spaghetti Carbonara", "Margherita Pizza", "Mushroom Risotto"),
                    recipeCount = 9,
                    dayCount = 5,
                    mealType = MealType.DINNER,
                ),
                onRepeatClick = {},
                onDeleteClick = {},
            )
            SavedMealPlanCard(
                summary = SavedMealPlanSummary(
                    id = UUID.randomUUID(),
                    title = "Light week",
                    heroImageModel = null,
                    recipeTitles = emptyList(),
                    recipeCount = 1,
                    dayCount = 1,
                    mealType = MealType.DINNER_AND_LUNCH,
                ),
                onRepeatClick = {},
                onDeleteClick = {},
            )
        }
    }
}
