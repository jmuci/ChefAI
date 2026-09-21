package com.tenmilelabs.chefai.recipes.ui.details.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.tenmilelabs.chefai.core.ui.components.flat.SectionRule
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/**
 * The recipe detail's stats bar (05): three equal columns — Prep, Cook, and a live Servings
 * stepper — between 2dp rules. Prep/Cook are read-only values; Servings is the only interactive
 * one, so it is the only column that takes a callback.
 */
@Composable
fun RecipeStatsBar(
    prepTimeMinutes: Int,
    cookTimeMinutes: Int,
    servings: Int,
    servingsRange: IntRange,
    onServingsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isServingsEstimated: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatColumn(label = stringResource(R.string.recipe_stat_prep)) {
                Text(
                    text = stringResource(R.string.recipe_time_minutes_format, prepTimeMinutes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            StatColumn(label = stringResource(R.string.recipe_stat_cook)) {
                Text(
                    text = stringResource(R.string.recipe_time_minutes_format, cookTimeMinutes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            StatColumn(label = stringResource(R.string.recipe_stat_servings)) {
                ServingsStepper(
                    servings = servings,
                    range = servingsRange,
                    onServingsChange = onServingsChange,
                )
            }
        }
        if (isServingsEstimated) {
            Text(
                text = stringResource(R.string.portions_estimated, servings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
        }
        SectionRule()
    }
}

@Composable
private fun StatColumn(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Preview(name = "Stats bar", showBackground = true)
@Preview(name = "Stats bar — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecipeStatsBarPreview() {
    ChefAITheme {
        RecipeStatsBar(
            prepTimeMinutes = 10,
            cookTimeMinutes = 15,
            servings = 4,
            servingsRange = 1..10,
            onServingsChange = {},
        )
    }
}

@Preview(name = "Stats bar — estimated servings", showBackground = true)
@Composable
private fun RecipeStatsBarEstimatedPreview() {
    ChefAITheme {
        RecipeStatsBar(
            prepTimeMinutes = 10,
            cookTimeMinutes = 15,
            servings = 4,
            servingsRange = 1..10,
            onServingsChange = {},
            isServingsEstimated = true,
        )
    }
}
