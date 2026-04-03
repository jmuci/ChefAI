package com.tenmilelabs.chefai.mealplans.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.mealplans.ui.components.WizardProgressBar
import com.tenmilelabs.chefai.mealplans.ui.create.components.DayLengthSelector
import com.tenmilelabs.chefai.mealplans.ui.create.components.MealTypeSelector
import com.tenmilelabs.chefai.mealplans.ui.create.components.ServingsSelector

@Composable
fun WizardBasicsScreen(
    viewModel: CreateMealPlanViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WizardBasicsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNext = onNext,
        modifier = modifier,
    )
}

@Composable
private fun WizardBasicsContent(
    uiState: CreateMealPlanUiState,
    onAction: (WizardAction) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        WizardProgressBar(
            currentStep = 0,
            totalSteps = uiState.totalSteps,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Text(
                text = "Let's plan your meals! 🍳",
                style = MaterialTheme.typography.headlineSmall,
            )

            DayLengthSelector(
                selectedDays = uiState.planLengthDays,
                onDaysSelected = { onAction(WizardAction.SetPlanLength(it)) },
            )

            MealTypeSelector(
                selectedType = uiState.mealType,
                onTypeSelected = { onAction(WizardAction.SetMealType(it)) },
            )

            ServingsSelector(
                servings = uiState.servingsPerMeal,
                onServingsChanged = { onAction(WizardAction.SetServings(it)) },
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.wizard_next))
        }
    }
}
