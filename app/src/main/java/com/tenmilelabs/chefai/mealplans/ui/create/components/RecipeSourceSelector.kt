package com.tenmilelabs.chefai.mealplans.ui.create.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeSourceSelector(
    selectedSource: RecipeSource,
    onSourceSelected: (RecipeSource) -> Unit,
    collectionTooSmall: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.wizard_recipe_source_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecipeSource.entries.forEach { source ->
                val isDisabled = source == RecipeSource.COLLECTION_ONLY && collectionTooSmall
                FilterChip(
                    selected = selectedSource == source,
                    onClick = { onSourceSelected(source) },
                    enabled = !isDisabled,
                    label = { Text("${source.emoji} ${source.label}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        if (collectionTooSmall) {
            Text(
                text = stringResource(R.string.wizard_recipe_source_collection_too_small),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
