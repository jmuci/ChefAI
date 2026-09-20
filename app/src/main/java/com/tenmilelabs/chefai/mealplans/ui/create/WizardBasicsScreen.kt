package com.tenmilelabs.chefai.mealplans.ui.create

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.ui.create.components.DayLengthSelector
import com.tenmilelabs.chefai.mealplans.ui.create.components.MealTypeSelector
import com.tenmilelabs.chefai.mealplans.ui.create.components.ServingsSelector
import com.tenmilelabs.chefai.mealplans.ui.create.components.WizardHeader

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
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun WizardBasicsContent(
    uiState: CreateMealPlanUiState,
    onAction: (WizardAction) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WizardHeader(
            currentStepIndex = 0,
            totalSteps = uiState.totalSteps,
            stepLabel = stringResource(R.string.wizard_step_basics),
            onClose = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
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

        FlatBlockButton(
            text = stringResource(R.string.wizard_next),
            onClick = onNext,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Wizard basics — light")
@Preview(name = "Wizard basics — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WizardBasicsPreview() {
    ChefAITheme {
        WizardBasicsContent(
            uiState = CreateMealPlanUiState(),
            onAction = {},
            onNext = {},
            onBack = {},
        )
    }
}
