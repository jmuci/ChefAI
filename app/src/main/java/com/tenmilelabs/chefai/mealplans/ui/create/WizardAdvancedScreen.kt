package com.tenmilelabs.chefai.mealplans.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.mealplans.ui.components.WizardProgressBar
import com.tenmilelabs.chefai.mealplans.ui.create.components.PrepTimeSelector
import com.tenmilelabs.chefai.mealplans.ui.create.components.ToggleOptionRow

@Composable
fun WizardAdvancedScreen(
    viewModel: CreateMealPlanViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WizardAdvancedContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardAdvancedContent(
    uiState: CreateMealPlanUiState,
    onAction: (WizardAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        WizardProgressBar(
            currentStep = 2,
            totalSteps = uiState.totalSteps,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "Final touches ✨",
                style = MaterialTheme.typography.headlineSmall,
            )

            ToggleOptionRow(
                title = stringResource(R.string.wizard_batch_cooking_title),
                subtitle = stringResource(R.string.wizard_batch_cooking_subtitle),
                checked = uiState.batchCooking,
                onCheckedChange = { onAction(WizardAction.SetBatchCooking(it)) },
            )

            ToggleOptionRow(
                title = stringResource(R.string.wizard_leftover_title),
                subtitle = stringResource(R.string.wizard_leftover_subtitle),
                checked = uiState.leftoverFriendly,
                onCheckedChange = { onAction(WizardAction.SetLeftoverFriendly(it)) },
            )

            PrepTimeSelector(
                selectedMinutes = uiState.maxPrepTimeMinutes,
                onMinutesSelected = { onAction(WizardAction.SetMaxPrepTime(it)) },
            )

            Column {
                Text(
                    text = stringResource(R.string.wizard_variety_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VarietyPreference.entries.forEach { pref ->
                        FilterChip(
                            selected = uiState.varietyPreference == pref,
                            onClick = { onAction(WizardAction.SetVarietyPreference(pref)) },
                            label = { Text("${pref.emoji} ${pref.label}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                enabled = !uiState.isSaving,
            ) {
                Text(stringResource(R.string.wizard_back))
            }
            Button(
                onClick = { onAction(WizardAction.SaveMealPlan) },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isSaving,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.wizard_create_plan))
                }
            }
        }
    }
}
