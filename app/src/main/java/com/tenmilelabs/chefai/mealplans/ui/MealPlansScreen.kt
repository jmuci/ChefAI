package com.tenmilelabs.chefai.mealplans.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.components.flat.SectionRule
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.mealplans.domain.week.MealPlanWeek
import com.tenmilelabs.chefai.mealplans.domain.week.PlannedWeekMeal
import com.tenmilelabs.chefai.mealplans.domain.week.WeekDay
import com.tenmilelabs.chefai.mealplans.domain.summary.MealPlanSummary
import com.tenmilelabs.chefai.mealplans.ui.components.AddMealSheet
import com.tenmilelabs.chefai.mealplans.ui.components.MealPlanHeroCard
import com.tenmilelabs.chefai.mealplans.ui.components.WeekDayRow
import com.tenmilelabs.chefai.mealplans.ui.components.WeekNavigator
import java.time.LocalDate
import java.util.UUID

/**
 * Bottom space the lists reserve for the "+" FAB the nav graph's Scaffold floats over this tab:
 * 56dp button + 16dp margin, plus 16dp of air so the last control never sits under it.
 */
private val FabClearance = 88.dp

/**
 * Screen 06 — the week view, or (via the header toggle) the list of every plan.
 *
 * Date-first, not plan-first: seven rows, Mon–Sun (or whatever the locale calls the first day),
 * each holding the meals that land on that date. See ADR-015 for how plans are projected onto it
 * and what happens when a week spans two of them or none.
 */
@Composable
fun MealPlansScreen(
    snackbarHostState: SnackbarHostState,
    onSearchRecipe: () -> Unit = {},
    onNewRecipe: () -> Unit = {},
    onMealClick: (PlannedWeekMeal) -> Unit = {},
    onShoppingListClick: (UUID) -> Unit = {},
    onPlanClick: (UUID) -> Unit = {},
    viewModel: MealPlansViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val addMealDate by viewModel.addMealDate.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                is MealPlansEvent.ShowMessage ->
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onTabResumed() }

    addMealDate?.let { date ->
        AddMealSheet(
            date = date,
            onSearchRecipe = { slot ->
                viewModel.onAddMealSourceChosen(slot)
                onSearchRecipe()
            },
            onNewRecipe = { slot ->
                viewModel.onAddMealSourceChosen(slot)
                onNewRecipe()
            },
            onDismiss = viewModel::onAddMealDismissed,
        )
    }

    MealPlansContent(
        uiState = uiState,
        listState = listState,
        viewMode = viewMode,
        onPlanClick = onPlanClick,
        onPreviousWeek = viewModel::onPreviousWeek,
        onNextWeek = viewModel::onNextWeek,
        onMealClick = onMealClick,
        onAddMeal = viewModel::onAddMeal,
        onGenerateGroceryList = onShoppingListClick,
        modifier = modifier,
    )
}

@Composable
private fun MealPlansContent(
    uiState: MealPlansUiState,
    listState: MealPlanListUiState,
    viewMode: MealPlansViewMode,
    onPlanClick: (UUID) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onMealClick: (PlannedWeekMeal) -> Unit,
    onAddMeal: (LocalDate) -> Unit,
    onGenerateGroceryList: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (viewMode == MealPlansViewMode.PLANS) {
        PlanListContent(
            listState = listState,
            onPlanClick = onPlanClick,
            modifier = modifier,
        )
        return
    }

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
private fun PlanListContent(
    listState: MealPlanListUiState,
    onPlanClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (listState) {
        is MealPlanListUiState.Loading -> LoadingContent(modifier = modifier)

        is MealPlanListUiState.Error -> EmptyContent(
            title = R.string.meal_plan_error,
            subtitle = R.string.no_meal_plans_subtitle,
            noRecipesIconRes = R.drawable.ic_skillet_cooktop_24dp,
            modifier = modifier,
        )

        is MealPlanListUiState.Success -> if (listState.plans.isEmpty()) {
            EmptyContent(
                title = R.string.no_meal_plans_title,
                subtitle = R.string.no_meal_plans_subtitle,
                noRecipesIconRes = R.drawable.ic_skillet_cooktop_24dp,
                modifier = modifier,
            )
        } else {
            PlanList(
                plans = listState.plans,
                today = listState.today,
                onPlanClick = onPlanClick,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PlanList(
    plans: List<MealPlanSummary>,
    today: LocalDate,
    onPlanClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.chefColors.panelGround),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = FabClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(plans, key = { it.planId }) { summary ->
            MealPlanHeroCard(
                summary = summary,
                today = today,
                onClick = { onPlanClick(summary.planId) },
                // Picking a date for a plan is a follow-up; the button is here so the card's
                // shape is final.
                onScheduleClick = {},
            )
        }
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
        // The FAB floats over the bottom-right; scrolling past the button keeps it clear.
        Spacer(Modifier.height(FabClearance))
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
        val meals = pluralStringResource(
            R.plurals.meal_plans_meals_planned,
            uiState.mealCount,
            uiState.mealCount,
        )
        val ingredients = pluralStringResource(
            R.plurals.meal_plans_ingredient_count,
            uiState.ingredientCount,
            uiState.ingredientCount,
        )
        val planCount = uiState.week.planIds.size
        Text(
            // Overlapping plans are all counted, so say so rather than present a merged figure as
            // if it were one plan's.
            text = if (planCount > 1) {
                stringResource(
                    R.string.meal_plans_grocery_summary_multi,
                    meals,
                    pluralStringResource(R.plurals.meal_plans_plan_count, planCount, planCount),
                    ingredients,
                )
            } else {
                stringResource(R.string.meal_plans_grocery_summary, meals, ingredients)
            },
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
            listState = MealPlanListUiState.Loading,
            viewMode = MealPlansViewMode.WEEK,
            onPlanClick = {},
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
                ingredientCount = 0,
            ),
            onPreviousWeek = {},
            onNextWeek = {},
            onMealClick = {},
            onAddMeal = {},
            onGenerateGroceryList = {},
            listState = MealPlanListUiState.Loading,
            viewMode = MealPlansViewMode.WEEK,
            onPlanClick = {},
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
        ingredientCount = 24,
    )
}
