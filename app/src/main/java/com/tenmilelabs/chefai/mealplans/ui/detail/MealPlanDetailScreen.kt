package com.tenmilelabs.chefai.mealplans.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.components.flat.SectionRule
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import com.tenmilelabs.chefai.mealplans.ui.components.MealPlanMealRow
import com.tenmilelabs.chefai.mealplans.ui.detail.print.printMealPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun MealPlanDetailScreen(
    onMealClick: (PlannedMeal) -> Unit,
    onShoppingListClick: () -> Unit,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    viewModel: MealPlanDetailViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MealPlanDetailEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is MealPlanDetailEvent.PrintReady -> printMealPlan(context, event.document)
            }
        }
    }

    when (val state = uiState) {
        is MealPlanDetailUiState.Loading -> LoadingContent(modifier = modifier)
        is MealPlanDetailUiState.NotFound -> EmptyContent(
            title = R.string.meal_plan_not_found,
            subtitle = R.string.meal_plan_not_found_subtitle,
            noRecipesIconRes = R.drawable.ic_skillet_cooktop_24dp,
            modifier = modifier,
        )
        is MealPlanDetailUiState.Success -> MealPlanDetailContent(
            state = state,
            onMealClick = onMealClick,
            onToggleCooked = viewModel::onToggleCooked,
            onGenerate = viewModel::onGenerate,
            onShoppingListClick = onShoppingListClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun MealPlanDetailContent(
    state: MealPlanDetailUiState.Success,
    onMealClick: (PlannedMeal) -> Unit,
    onToggleCooked: (PlannedMeal) -> Unit,
    onGenerate: () -> Unit,
    onShoppingListClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Preferences start collapsed: they were chosen moments ago in the wizard, so the week itself
    // is what the user came here for.
    var preferencesExpanded by rememberSaveable { mutableStateOf(false) }
    val isGenerating = state.isGenerating || state.mealPlan.status == MealPlanStatus.GENERATING

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        ) {
            if (state.totalCount > 0) {
                item(key = "progress") {
                    ProgressHeader(state = state, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(16.dp))
                }
            }

            when {
                isGenerating -> item(key = "generating") {
                    GeneratingState(modifier = Modifier.padding(horizontal = 16.dp))
                }
                state.totalCount == 0 -> item(key = "empty") {
                    EmptyPlanState(onGenerate = onGenerate, modifier = Modifier.padding(horizontal = 16.dp))
                }
                else -> {
                    item(key = "rule-top") { SectionRule() }
                    itemsIndexed(
                        items = state.meals,
                        key = { _, meal -> "meal-${meal.dayId}-${meal.slot}" },
                    ) { index, meal ->
                        if (index > 0) RowRule()
                        val date = remember(meal.dayIndex, state.mealPlan.createdAt) {
                            mealPlanDateFor(state.mealPlan.createdAt, meal.dayIndex)
                        }
                        MealPlanMealRow(
                            recipe = meal.recipe,
                            dayAbbreviation = date.shortDayName(),
                            dayNumber = date.dayOfMonth.toString(),
                            isToday = date == LocalDate.now(),
                            isCooked = meal.isCooked,
                            slotLabel = if (state.showsSlotLabels) meal.slot.label else null,
                            onClick = { onMealClick(meal) },
                            onToggleCooked = { onToggleCooked(meal) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    item(key = "rule-bottom") { SectionRule() }
                }
            }

            item(key = "preferences") {
                Spacer(Modifier.height(16.dp))
                PreferencesDisclosure(
                    mealPlan = state.mealPlan,
                    expanded = preferencesExpanded,
                    onToggle = { preferencesExpanded = !preferencesExpanded },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (state.totalCount > 0) {
            SectionRule()
            FlatBlockButton(
                text = stringResource(R.string.meal_plan_shopping_list_button, state.shoppingListItemCount),
                onClick = onShoppingListClick,
                leadingIcon = ChefAIIcons.ShoppingCart,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private fun LocalDate.shortDayName(): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase()

/**
 * "Aug 11 – 15" — the plan's day range, derived from [MealPlan.createdAt] treated as day zero (the
 * domain model carries no per-day date). Exposed so the app shell's header — which owns the
 * meal-plan-detail top bar — can build the same subtitle it shows next to the plan title.
 */
fun mealPlanDateRangeLabel(mealPlan: MealPlan): String {
    val start = mealPlanDateFor(mealPlan.createdAt, 0)
    val end = mealPlanDateFor(mealPlan.createdAt, (mealPlan.preferences.planLengthDays - 1).coerceAtLeast(0))
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    return if (start.month == end.month) {
        "${formatter.format(start)} – ${end.dayOfMonth}"
    } else {
        "${formatter.format(start)} – ${formatter.format(end)}"
    }
}

/** The header subtitle: the date range, plus "Shared by <name>" when the plan is shared. */
@Composable
fun mealPlanDetailSubtitle(mealPlan: MealPlan, ownerDisplayName: String?): String {
    val range = mealPlanDateRangeLabel(mealPlan)
    return if (ownerDisplayName != null) {
        "$range · " + stringResource(R.string.meal_plan_shared_by, ownerDisplayName)
    } else {
        range
    }
}

/**
 * "3 of 5 meals cooked" plus how much of the week is done, as one segment per meal — accent when
 * cooked, neutral when not. Replaces a continuous bar: the segment count *is* the plan length.
 */
@Composable
private fun ProgressHeader(
    state: MealPlanDetailUiState.Success,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = when {
                    state.cookedCount == state.totalCount ->
                        stringResource(R.string.meal_plan_progress_complete)
                    state.cookedCount == 0 ->
                        stringResource(R.string.meal_plan_progress_none)
                    else -> stringResource(
                        R.string.meal_plan_progress,
                        state.cookedCount,
                        state.totalCount,
                    )
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${(state.progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        MealProgressSegments(meals = state.meals)
    }
}

@Composable
private fun MealProgressSegments(
    meals: List<PlannedMeal>,
    modifier: Modifier = Modifier,
) {
    if (meals.isEmpty()) return
    val completed = MaterialTheme.colorScheme.primary
    val remaining = MaterialTheme.chefColors.neutral.s200

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        meals.forEach { meal ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(if (meal.isCooked) completed else remaining),
            )
        }
    }
}

/** The plan's wizard answers, collapsed behind a disclosure row so they don't crowd out the week. */
@Composable
private fun PreferencesDisclosure(
    mealPlan: MealPlan,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "preferencesChevron",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .flatClickable(onClick = onToggle, role = Role.Button)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.meal_plan_preferences_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Icon(
                painter = painterResource(ChefAIIcons.ChevronDown),
                contentDescription = stringResource(
                    if (expanded) R.string.meal_plan_preferences_hide else R.string.meal_plan_preferences_show
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                val prefs = mealPlan.preferences

                PreferenceRow("Meals", prefs.mealType.label)
                PreferenceRow("Duration", "${prefs.planLengthDays} days")
                PreferenceRow("Servings", "${prefs.servingsPerMeal} per meal")

                val dietary = prefs.dietaryRestrictions.filter { it != DietaryRestriction.NONE }
                if (dietary.isNotEmpty()) {
                    PreferenceRow("Dietary", dietary.joinToString(", ") { it.label })
                }

                prefs.maxPrepTimeMinutes?.let { PreferenceRow("Max prep", "$it min") }
                PreferenceRow("Recipes from", prefs.recipeSource.label)
                PreferenceRow("Variety", prefs.varietyPreference.label)

                val extras = buildList {
                    if (prefs.batchCooking) add("Batch cooking")
                    if (prefs.leftoverFriendly) add("Leftover-friendly")
                }
                if (extras.isNotEmpty()) {
                    PreferenceRow("Options", extras.joinToString(", "))
                }
            }
        }
    }
}

@Composable
private fun PreferenceRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun EmptyPlanState(
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.meal_plan_not_generated_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.meal_plan_not_generated_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        FlatBlockButton(
            text = stringResource(R.string.meal_plan_generate),
            onClick = onGenerate,
            leadingIcon = ChefAIIcons.Sparkles,
        )
    }
}

@Composable
private fun GeneratingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.meal_plan_generating),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// region Previews

private val previewPreferences = MealPlanPreferences(
    planLengthDays = 5,
    mealType = MealType.DINNER,
    dietaryRestrictions = setOf(DietaryRestriction.VEGETARIAN),
    recipeSource = RecipeSource.INCLUDE_PUBLIC,
    maxPrepTimeMinutes = 30,
    servingsPerMeal = 2,
    batchCooking = false,
    leftoverFriendly = true,
    varietyPreference = VarietyPreference.HIGH,
)

private fun previewState(): MealPlanDetailUiState.Success {
    val plan = MealPlan(
        uuid = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        name = "Week plan",
        status = MealPlanStatus.READY,
        preferences = previewPreferences,
        createdAt = 0L,
        updatedAt = 0L,
        days = emptyList(),
    )
    fun meal(day: Int, cookedAt: Long?) = PlannedMeal(
        dayId = UUID.randomUUID(),
        dayIndex = day,
        slot = MealSlot.DINNER,
        recipeId = UUID.randomUUID(),
        recipe = null,
        cookedAt = cookedAt,
    )

    return MealPlanDetailUiState.Success(
        mealPlan = plan,
        board = MealPlanBoard(
            meals = listOf(
                meal(0, 2L),
                meal(1, 1L),
                meal(2, 3L),
                meal(3, null),
                meal(4, null),
            ),
        ),
        shoppingListItemCount = 24,
    )
}

@Preview(name = "Detail — Light", showBackground = true, heightDp = 900)
@Composable
private fun MealPlanDetailLightPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanDetailContent(
            state = previewState(),
            onMealClick = {},
            onToggleCooked = {},
            onGenerate = {},
            onShoppingListClick = {},
        )
    }
}

@Preview(name = "Detail — Dark", showBackground = true, heightDp = 900)
@Composable
private fun MealPlanDetailDarkPreview() {
    ChefAITheme(darkTheme = true) {
        MealPlanDetailContent(
            state = previewState(),
            onMealClick = {},
            onToggleCooked = {},
            onGenerate = {},
            onShoppingListClick = {},
        )
    }
}

@Preview(name = "Detail — empty plan", showBackground = true, heightDp = 700)
@Composable
private fun MealPlanDetailEmptyPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanDetailContent(
            state = previewState().copy(board = MealPlanBoard(meals = emptyList())),
            onMealClick = {},
            onToggleCooked = {},
            onGenerate = {},
            onShoppingListClick = {},
        )
    }
}

// endregion
