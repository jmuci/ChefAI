package com.tenmilelabs.chefai.mealplans.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.components.flat.SectionRule
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanServingBasis
import com.tenmilelabs.chefai.mealplans.domain.week.MealPlanWeek
import com.tenmilelabs.chefai.mealplans.domain.week.PlannedWeekMeal
import com.tenmilelabs.chefai.mealplans.domain.week.WeekDay
import com.tenmilelabs.chefai.mealplans.ui.components.WeekDayRow
import com.tenmilelabs.chefai.mealplans.ui.components.WeekNavigator
import java.time.LocalDate
import java.util.UUID

/**
 * Screen 06 — the week view.
 *
 * Date-first, not plan-first: seven rows, Mon–Sun (or whatever the locale calls the first day),
 * each holding the meals that land on that date. See ADR-015 for how plans are projected onto it
 * and what happens when a week spans two of them or none.
 */
@Composable
fun MealPlansScreen(
    onCreateMealPlan: () -> Unit = {},
    onMealClick: (PlannedWeekMeal) -> Unit = {},
    onShoppingListClick: (UUID) -> Unit = {},
    viewModel: MealPlansViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MealPlansContent(
        uiState = uiState,
        onPreviousWeek = viewModel::onPreviousWeek,
        onNextWeek = viewModel::onNextWeek,
        onMealClick = onMealClick,
        // "+ Add meal" has no picker yet, so it routes to the wizard — which is also the only
        // remaining way into plan creation now that the plan list is gone. ADR-015 Consequences.
        onAddMeal = { onCreateMealPlan() },
        onGenerateGroceryList = onShoppingListClick,
        modifier = modifier,
    )
}

@Composable
private fun MealPlansContent(
    uiState: MealPlansUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onMealClick: (PlannedWeekMeal) -> Unit,
    onAddMeal: (LocalDate) -> Unit,
    onGenerateGroceryList: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is MealPlansUiState.Loading -> LoadingContent(modifier = modifier)

        is MealPlansUiState.Error -> EmptyContent(
            title = R.string.meal_plan_error,
            subtitle = R.string.no_meal_plans_subtitle,
            noRecipesIconRes = R.drawable.ic_skillet_cooktop_24dp,
            modifier = modifier,
        )

        is MealPlansUiState.Success -> WeekContent(
            uiState = uiState,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onMealClick = onMealClick,
            onAddMeal = onAddMeal,
            onGenerateGroceryList = onGenerateGroceryList,
            modifier = modifier,
        )
    }
}

@Composable
private fun WeekContent(
    uiState: MealPlansUiState.Success,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onMealClick: (PlannedWeekMeal) -> Unit,
    onAddMeal: (LocalDate) -> Unit,
    onGenerateGroceryList: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The whole week scrolls as one: seven rows plus the grocery block overflow a short
            // screen, and a LazyColumn would buy nothing for a list that is always seven long.
            .verticalScroll(rememberScrollState()),
    ) {
        WeekNavigator(
            weekStart = uiState.week.weekStart,
            weekEnd = uiState.week.weekEnd,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
        )

        // The week's own ruled group: 2dp above, 1dp between days, 2dp below.
        SectionRule()
        uiState.week.days.forEachIndexed { index, day ->
            if (index > 0) RowRule()
            WeekDayRow(
                day = day,
                isToday = day.date == uiState.today,
                onMealClick = onMealClick,
                onAddMeal = onAddMeal,
            )
        }
        SectionRule()

        GrocerySummary(
            uiState = uiState,
            onGenerateGroceryList = onGenerateGroceryList,
        )
    }
}

@Composable
private fun GrocerySummary(
    uiState: MealPlansUiState.Success,
    onGenerateGroceryList: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.meal_plans_grocery_list_label).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.chefColors.accentText,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.meal_plans_grocery_summary,
                pluralStringResource(
                    R.plurals.meal_plans_meals_planned,
                    uiState.mealCount,
                    uiState.mealCount,
                ),
                pluralStringResource(
                    R.plurals.meal_plans_ingredient_count,
                    uiState.ingredientCount,
                    uiState.ingredientCount,
                ),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        val planId = uiState.week.singlePlanId
        FlatBlockButton(
            text = stringResource(R.string.meal_plans_generate_grocery_list),
            onClick = { planId?.let(onGenerateGroceryList) },
            enabled = planId != null,
        )
        // A disabled button with no reason beside it is just a dead control. The line says which
        // of the two cases this is — nothing planned, or more plans than one list can shop for.
        if (planId == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (uiState.mealCount == 0) {
                        R.string.meal_plans_grocery_unavailable_empty
                    } else {
                        R.string.meal_plans_grocery_unavailable_multi
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Meal Plans — light")
@Preview(name = "Meal Plans — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MealPlansPreview() {
    ChefAITheme {
        MealPlansContent(
            uiState = previewState(),
            onPreviousWeek = {},
            onNextWeek = {},
            onMealClick = {},
            onAddMeal = {},
            onGenerateGroceryList = {},
        )
    }
}

@Preview(name = "Meal Plans — empty week, light")
@Preview(name = "Meal Plans — empty week, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MealPlansEmptyWeekPreview() {
    val start = LocalDate.of(2026, 8, 11)
    ChefAITheme {
        MealPlansContent(
            uiState = MealPlansUiState.Success(
                week = MealPlanWeek(
                    weekStart = start,
                    days = (0..6).map { WeekDay(start.plusDays(it.toLong()), emptyList()) },
                    planIds = emptySet(),
                ),
                today = start.plusDays(1),
                servingBasis = MealPlanServingBasis.JUST_ME,
                household = null,
                ingredientCount = 0,
            ),
            onPreviousWeek = {},
            onNextWeek = {},
            onMealClick = {},
            onAddMeal = {},
            onGenerateGroceryList = {},
        )
    }
}

private fun previewState(): MealPlansUiState.Success {
    val start = LocalDate.of(2026, 8, 11)
    val planId = UUID.randomUUID()
    val days = (0..6).map { offset ->
        WeekDay(date = start.plusDays(offset.toLong()), meals = emptyList())
    }
    return MealPlansUiState.Success(
        week = MealPlanWeek(weekStart = start, days = days, planIds = setOf(planId)),
        today = start.plusDays(1),
        servingBasis = MealPlanServingBasis.JUST_ME,
        household = null,
        ingredientCount = 24,
    )
}
