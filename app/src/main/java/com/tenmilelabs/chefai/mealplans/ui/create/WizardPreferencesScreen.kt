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
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.ui.create.components.DietaryChipGroup
import com.tenmilelabs.chefai.mealplans.ui.create.components.RecipeSourceSelector
import com.tenmilelabs.chefai.mealplans.ui.create.components.WizardHeader

@Composable
fun WizardPreferencesScreen(
    viewModel: CreateMealPlanViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WizardPreferencesContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNext = onNext,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun WizardPreferencesContent(
    uiState: CreateMealPlanUiState,
    onAction: (WizardAction) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WizardHeader(
            currentStepIndex = 1,
            totalSteps = uiState.totalSteps,
            stepLabel = stringResource(R.string.wizard_step_preferences),
            onClose = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            DietaryChipGroup(
                selectedRestrictions = uiState.dietaryRestrictions,
                onToggle = { onAction(WizardAction.ToggleDietaryRestriction(it)) },
            )

            RecipeSourceSelector(
                selectedSource = uiState.recipeSource,
                onSourceSelected = { onAction(WizardAction.SetRecipeSource(it)) },
                collectionTooSmall = uiState.collectionTooSmall,
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
                modifier = Modifier.weight(1f),
            )
            FlatBlockButton(
                text = stringResource(R.string.wizard_next),
                onClick = onNext,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(name = "Wizard preferences — light")
@Preview(name = "Wizard preferences — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WizardPreferencesPreview() {
    ChefAITheme {
        WizardPreferencesContent(
            uiState = CreateMealPlanUiState(),
            onAction = {},
            onNext = {},
            onBack = {},
        )
    }
}
