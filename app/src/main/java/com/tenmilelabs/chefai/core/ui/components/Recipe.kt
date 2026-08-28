package com.tenmilelabs.chefai.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.tenmilelabs.chefai.R

@Composable
fun RecipeTimeRow(prepTime: Int, cookTime: Int) {
    val totalTime = prepTime + cookTime
    Text(
        text = "Prep: ${prepTime}m  ·  Cook: ${cookTime}m  ·  Total: ${totalTime}m",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Calories/protein per serving, as published by the recipe's source or entered by hand. Renders
 * nothing when both are `null` — most recipes won't have this yet, and an empty row would just be
 * noise. Renders whichever one value is available when only one is set, rather than hiding it.
 */
@Composable
fun NutritionRow(caloriesPerServing: Int?, proteinGramsPerServing: Int?) {
    if (caloriesPerServing == null && proteinGramsPerServing == null) return

    val parts = listOfNotNull(
        caloriesPerServing?.let { stringResource(R.string.nutrition_calories_format, it) },
        proteinGramsPerServing?.let { stringResource(R.string.nutrition_protein_format, it) },
    )
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}