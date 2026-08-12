package com.tenmilelabs.chefai.mealplans.ui.detail

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.LargeCard
import androidx.compose.runtime.LaunchedEffect
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
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
                is MealPlanDetailEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
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
            mealPlan = state.mealPlan,
            dayItems = state.dayItems,
            onRecipeClick = onRecipeClick,
            onGenerate = viewModel::onGenerate,
            modifier = modifier,
        )
    }
}

@Composable
private fun MealPlanDetailContent(
    mealPlan: MealPlan,
    dayItems: List<DayItem>,
    onRecipeClick: (UUID) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Preferences summary card
        item(key = "preferences") {
            PreferencesSummaryCard(mealPlan = mealPlan)
        }

        // Day items: headers + recipe cards
        itemsIndexed(
            items = dayItems,
            key = { index, item ->
                when (item) {
                    is DayItem.Header -> "header-$index-${item.label}"
                    is DayItem.RecipeCard -> "recipe-$index-${item.recipeId}"
                    is DayItem.MissingRecipe -> "missing-$index-${item.recipeId}"
                }
            }
        ) { _, item ->
            when (item) {
                is DayItem.Header -> DayHeader(label = item.label)
                is DayItem.RecipeCard -> LargeCard(
                    recipe = item.recipe,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    onClick = { onRecipeClick(item.recipeId) },
                )
                is DayItem.MissingRecipe -> MissingRecipeCard()
            }
        }

        // Generate action for DRAFT plans
        if (mealPlan.status == MealPlanStatus.DRAFT) {
            item(key = "generate-action") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "This meal plan hasn't been generated yet.",
                        style = MaterialTheme.typography.bodyLarge,
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
                        Text("Generate Meal Plan")
                    }
                }
            }
        }

        // Generating state — show spinner
        if (mealPlan.status == MealPlanStatus.GENERATING) {
            item(key = "generating") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your meal plan is being generated...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferencesSummaryCard(
    mealPlan: MealPlan,
    modifier: Modifier = Modifier,
) {
    val prefs = mealPlan.preferences
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = mealPlan.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatusText(status = mealPlan.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            PreferenceRow(label = "Meals", value = "${prefs.mealType.emoji} ${prefs.mealType.label}")
            PreferenceRow(label = "Duration", value = "${prefs.planLengthDays} days")
            PreferenceRow(label = "Servings", value = "${prefs.servingsPerMeal} per meal")

            val dietary = prefs.dietaryRestrictions.filter { it != DietaryRestriction.NONE }
            if (dietary.isNotEmpty()) {
                PreferenceRow(
                    label = "Dietary",
                    value = dietary.joinToString(", ") { "${it.emoji} ${it.label}" }
                )
            }

            prefs.maxPrepTimeMinutes?.let {
                PreferenceRow(label = "Max prep", value = "${it} min")
            }
            PreferenceRow(label = "Recipes from", value = "${prefs.recipeSource.emoji} ${prefs.recipeSource.label}")
            PreferenceRow(label = "Variety", value = "${prefs.varietyPreference.emoji} ${prefs.varietyPreference.label}")

            val extras = buildList {
                if (prefs.batchCooking) add("Batch cooking")
                if (prefs.leftoverFriendly) add("Leftover-friendly")
            }
            if (extras.isNotEmpty()) {
                PreferenceRow(label = "Options", value = extras.joinToString(", "))
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
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
        MealPlanStatus.GENERATING -> "Generating..." to MaterialTheme.colorScheme.primary
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
private fun DayHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
private fun MissingRecipeCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Text(
            text = "Recipe not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
