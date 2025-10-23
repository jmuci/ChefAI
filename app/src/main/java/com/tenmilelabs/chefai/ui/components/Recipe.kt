package com.tenmilelabs.chefai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

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