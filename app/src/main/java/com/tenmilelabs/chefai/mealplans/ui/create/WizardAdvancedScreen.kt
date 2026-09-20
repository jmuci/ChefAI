package com.tenmilelabs.chefai.mealplans.ui.create

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.components.flat.RuledGroup
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.ui.create.components.PrepTimeSelector
import com.tenmilelabs.chefai.mealplans.ui.create.components.ToggleOptionRow
import com.tenmilelabs.chefai.mealplans.ui.create.components.VarietyPreferenceSelector
import com.tenmilelabs.chefai.mealplans.ui.create.components.WizardHeader

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

@Composable
private fun WizardAdvancedContent(
    uiState: CreateMealPlanUiState,
    onAction: (WizardAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WizardHeader(
            currentStepIndex = 2,
            totalSteps = uiState.totalSteps,
            stepLabel = stringResource(R.string.wizard_step_extras),
            onClose = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RuledGroup {
                row {
                    ToggleOptionRow(
                        title = stringResource(R.string.wizard_batch_cooking_title),
                        subtitle = stringResource(R.string.wizard_batch_cooking_subtitle),
                        checked = uiState.batchCooking,
                        onCheckedChange = { onAction(WizardAction.SetBatchCooking(it)) },
                    )
                }
                row {
                    ToggleOptionRow(
                        title = stringResource(R.string.wizard_leftover_title),
                        subtitle = stringResource(R.string.wizard_leftover_subtitle),
                        checked = uiState.leftoverFriendly,
                        onCheckedChange = { onAction(WizardAction.SetLeftoverFriendly(it)) },
                    )
                }
            }

            PrepTimeSelector(
                selectedMinutes = uiState.maxPrepTimeMinutes,
                onMinutesSelected = { onAction(WizardAction.SetMaxPrepTime(it)) },
            )

            VarietyPreferenceSelector(
                selectedPreference = uiState.varietyPreference,
                onPreferenceSelected = { onAction(WizardAction.SetVarietyPreference(it)) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlatBlockButton(
                text = stringResource(R.string.wizard_back),
                onClick = onBack,
                variant = FlatButtonVariant.Secondary,
                enabled = !uiState.isSaving,
                modifier = Modifier.weight(1f),
            )
            FlatBlockButton(
                text = stringResource(R.string.wizard_create_plan),
                onClick = { onAction(WizardAction.SaveMealPlan) },
                enabled = !uiState.isSaving,
                loading = uiState.isSaving,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}

@Preview(name = "Wizard advanced — light")
@Preview(name = "Wizard advanced — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WizardAdvancedPreview() {
    ChefAITheme {
        WizardAdvancedContent(
            uiState = CreateMealPlanUiState(),
            onAction = {},
            onBack = {},
        )
    }
}

@Preview(name = "Wizard advanced — saving")
@Composable
private fun WizardAdvancedSavingPreview() {
    ChefAITheme {
        WizardAdvancedContent(
            uiState = CreateMealPlanUiState(isSaving = true),
            onAction = {},
            onBack = {},
        )
    }
}
