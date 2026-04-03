package com.tenmilelabs.chefai.mealplans.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.mealplans.ui.components.MealPlanCard
import java.util.UUID

@Composable
fun MealPlansScreen(
    onCreateMealPlan: () -> Unit = {},
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    viewModel: MealPlansViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is MealPlansEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(context.getString(event.message))
                }
            }
        }
    }

    MealPlansContent(
        uiState = uiState,
        onDeleteMealPlan = viewModel::onDeleteMealPlan,
        modifier = modifier,
    )
}

@Composable
private fun MealPlansContent(
    uiState: MealPlansUiState,
    onDeleteMealPlan: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is MealPlansUiState.Loading -> {
            LoadingContent(modifier = modifier)
        }

        is MealPlansUiState.Error -> {
            EmptyContent(
                title = R.string.meal_plan_error,
                subtitle = R.string.no_meal_plans_subtitle,
                noRecipesIconRes = R.drawable.ic_skillet_cooktop_24dp,
                modifier = modifier,
            )
        }

        is MealPlansUiState.Success -> {
            if (uiState.mealPlans.isEmpty()) {
                EmptyContent(
                    title = R.string.no_meal_plans_title,
                    subtitle = R.string.no_meal_plans_subtitle,
                    noRecipesIconRes = R.drawable.ic_skillet_cooktop_24dp,
                    modifier = modifier,
                )
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        items = uiState.mealPlans,
                        key = { it.uuid }
                    ) { mealPlan ->
                        MealPlanCard(
                            mealPlan = mealPlan,
                            onDelete = { onDeleteMealPlan(mealPlan.uuid) },
                        )
                    }
                }
            }
        }
    }
}
