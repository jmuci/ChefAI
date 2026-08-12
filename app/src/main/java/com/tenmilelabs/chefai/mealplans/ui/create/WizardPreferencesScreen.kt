package com.tenmilelabs.chefai.mealplans.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.ui.components.WizardProgressBar
import com.tenmilelabs.chefai.mealplans.ui.create.components.DietaryChipGroup
import com.tenmilelabs.chefai.mealplans.ui.create.components.RecipeSourceSelector

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        WizardProgressBar(
            currentStep = 1,
            totalSteps = uiState.totalSteps,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Text(
                text = "Tell us your preferences 🎯",
                style = MaterialTheme.typography.headlineSmall,
            )

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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.wizard_back))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.wizard_next))
            }
        }
    }
}
