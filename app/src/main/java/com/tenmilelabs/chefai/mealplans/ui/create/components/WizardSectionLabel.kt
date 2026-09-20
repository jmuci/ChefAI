package com.tenmilelabs.chefai.mealplans.ui.create.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A wizard question header — "HOW MANY DAYS?". Ink, not accent: unlike Settings' section label, none of the handoff's wizard headers use the accent step. */
@Composable
fun WizardSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(bottom = 12.dp),
    )
}
