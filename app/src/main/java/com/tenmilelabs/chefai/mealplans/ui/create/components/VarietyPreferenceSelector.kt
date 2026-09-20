package com.tenmilelabs.chefai.mealplans.ui.create.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatChip
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VarietyPreferenceSelector(
    selectedPreference: VarietyPreference,
    onPreferenceSelected: (VarietyPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WizardSectionLabel(stringResource(R.string.wizard_variety_title))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VarietyPreference.entries.forEach { preference ->
                FlatChip(
                    label = preference.label,
                    selected = selectedPreference == preference,
                    onClick = { onPreferenceSelected(preference) },
                    singleSelect = true,
                )
            }
        }
    }
}
