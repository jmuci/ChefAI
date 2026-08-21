package com.tenmilelabs.chefai.mealplans.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
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
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import java.util.UUID

@Composable
fun MealPlanDetailScreen(
    onRecipeClick: (UUID) -> Unit,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    viewModel: MealPlanDetailViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MealPlanDetailEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
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
            onRecipeClick = onRecipeClick,
            onToggleCooked = viewModel::onToggleCooked,
            onGenerate = viewModel::onGenerate,
            modifier = modifier,
        )
    }
}

@Composable
private fun MealPlanDetailContent(
    state: MealPlanDetailUiState.Success,
    onRecipeClick: (UUID) -> Unit,
    onToggleCooked: (PlannedMeal) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Preferences start collapsed: they were chosen moments ago in the wizard, so the week itself
    // is what the user came here for.
    var preferencesExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "progress") {
            ProgressHeader(state = state)
        }

        item(key = "preferences") {
            PreferencesSummary(
                mealPlan = state.mealPlan,
                expanded = preferencesExpanded,
                onToggle = { preferencesExpanded = !preferencesExpanded },
            )
        }

        if (state.isGenerating || state.mealPlan.status == MealPlanStatus.GENERATING) {
            item(key = "generating") { GeneratingState() }
        } else if (state.totalCount == 0) {
            item(key = "empty") { EmptyPlanState(onGenerate = onGenerate) }
        }

        state.upcoming.forEach { section ->
            item(key = "header-${section.dayIndex}") {
                SectionHeader(text = section.label)
            }
            items(
                items = section.meals,
                key = { meal -> "meal-${meal.dayId}-${meal.slot}" },
            ) { meal ->
                MealPlanMealRow(
                    recipe = meal.recipe,
                    isCooked = false,
                    slotLabel = if (state.showsSlotLabels) meal.slot.label else null,
                    onClick = { onRecipeClick(meal.recipeId) },
                    onToggleCooked = { onToggleCooked(meal) },
                )
            }
        }

        if (state.cooked.isNotEmpty()) {
            item(key = "cooked-header") {
                CookedSectionHeader(count = state.cooked.size)
            }
            items(
                items = state.cooked,
                key = { meal -> "cooked-${meal.dayId}-${meal.slot}" },
            ) { meal ->
                MealPlanMealRow(
                    recipe = meal.recipe,
                    isCooked = true,
                    dayLabel = meal.dayLabel,
                    slotLabel = if (state.showsSlotLabels) meal.slot.label else null,
                    onClick = { onRecipeClick(meal.recipeId) },
                    onToggleCooked = { onToggleCooked(meal) },
                )
            }
        }
    }
}

/**
 * Plan name, status, and how much of the week is done.
 *
 * The progress bar is the screen's anchor: the whole point of marking meals cooked is seeing this
 * fill up, so it sits above everything else and stays legible at a glance.
 */
@Composable
private fun ProgressHeader(
    state: MealPlanDetailUiState.Success,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(targetValue = state.progress, label = "planProgress")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.mealPlan.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatusText(status = state.mealPlan.status)
            }

            if (state.totalCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** The plan's wizard answers, collapsed behind a header so they don't crowd out the week. */
@Composable
private fun PreferencesSummary(
    mealPlan: MealPlan,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "preferencesChevron",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onToggle,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.meal_plan_preferences_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) {
                                R.string.meal_plan_preferences_hide
                            } else {
                                R.string.meal_plan_preferences_show
                            }
                        ),
                        modifier = Modifier.rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    val prefs = mealPlan.preferences

                    PreferenceRow("Meals", "${prefs.mealType.emoji} ${prefs.mealType.label}")
                    PreferenceRow("Duration", "${prefs.planLengthDays} days")
                    PreferenceRow("Servings", "${prefs.servingsPerMeal} per meal")

                    val dietary = prefs.dietaryRestrictions.filter { it != DietaryRestriction.NONE }
                    if (dietary.isNotEmpty()) {
                        PreferenceRow(
                            "Dietary",
                            dietary.joinToString(", ") { "${it.emoji} ${it.label}" },
                        )
                    }

                    prefs.maxPrepTimeMinutes?.let { PreferenceRow("Max prep", "$it min") }
                    PreferenceRow(
                        "Recipes from",
                        "${prefs.recipeSource.emoji} ${prefs.recipeSource.label}",
                    )
                    PreferenceRow(
                        "Variety",
                        "${prefs.varietyPreference.emoji} ${prefs.varietyPreference.label}",
                    )

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
            .padding(vertical = 3.dp),
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
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatusText(
    status: MealPlanStatus,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when (status) {
        MealPlanStatus.DRAFT -> "Draft" to MaterialTheme.colorScheme.tertiary
        MealPlanStatus.GENERATING -> "Generating…" to MaterialTheme.colorScheme.primary
        MealPlanStatus.READY -> "Ready" to MaterialTheme.colorScheme.primary
        MealPlanStatus.ARCHIVED -> "Archived" to MaterialTheme.colorScheme.outline
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

/** Divider plus heading that separates the done pile from the week still ahead. */
@Composable
private fun CookedSectionHeader(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 20.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.meal_plan_cooked_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.meal_plan_not_generated_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGenerate) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.meal_plan_generate))
        }
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
        CircularProgressIndicator()
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
    planLengthDays = 3,
    mealType = MealType.DINNER_AND_LUNCH,
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
        name = "3-day meal plan",
        status = MealPlanStatus.READY,
        preferences = previewPreferences,
        createdAt = 0L,
        updatedAt = 0L,
        days = emptyList(),
    )
    fun meal(day: Int, slot: MealSlot, cookedAt: Long?) = PlannedMeal(
        dayId = UUID.randomUUID(),
        dayIndex = day,
        slot = slot,
        recipeId = UUID.randomUUID(),
        recipe = null,
        cookedAt = cookedAt,
    )

    return MealPlanDetailUiState.Success(
        mealPlan = plan,
        board = MealPlanBoard(
            upcoming = listOf(
                DaySection(
                    1,
                    "Day 2",
                    listOf(meal(1, MealSlot.LUNCH, null), meal(1, MealSlot.DINNER, null)),
                ),
                DaySection(
                    2,
                    "Day 3",
                    listOf(meal(2, MealSlot.LUNCH, null), meal(2, MealSlot.DINNER, null)),
                ),
            ),
            cooked = listOf(meal(0, MealSlot.DINNER, 2L), meal(0, MealSlot.LUNCH, 1L)),
        ),
    )
}

@Preview(name = "Detail — Light", showBackground = true, heightDp = 900)
@Composable
private fun MealPlanDetailLightPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanDetailContent(
            state = previewState(),
            onRecipeClick = {},
            onToggleCooked = {},
            onGenerate = {},
        )
    }
}

@Preview(name = "Detail — Dark", showBackground = true, heightDp = 900)
@Composable
private fun MealPlanDetailDarkPreview() {
    ChefAITheme(darkTheme = true) {
        MealPlanDetailContent(
            state = previewState(),
            onRecipeClick = {},
            onToggleCooked = {},
            onGenerate = {},
        )
    }
}

@Preview(name = "Detail — empty plan", showBackground = true, heightDp = 700)
@Composable
private fun MealPlanDetailEmptyPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanDetailContent(
            state = previewState().copy(board = MealPlanBoard(upcoming = emptyList(), cooked = emptyList())),
            onRecipeClick = {},
            onToggleCooked = {},
            onGenerate = {},
        )
    }
}

// endregion
