package com.tenmilelabs.chefai.mealplans.ui.create.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.mealplans.domain.model.MealType

@Composable
fun MealTypeSelector(
    selectedType: MealType,
    onTypeSelected: (MealType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WizardSectionLabel(stringResource(R.string.wizard_meal_type_title))
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MealType.entries.forEach { type ->
                WizardOptionRow(
                    label = type.label,
                    selected = selectedType == type,
                    onClick = { onTypeSelected(type) },
                )
            }
        }
    }
}
