package com.tenmilelabs.chefai.mealplans.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanPreferences
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus
import com.tenmilelabs.chefai.mealplans.domain.model.MealType
import com.tenmilelabs.chefai.mealplans.domain.model.RecipeSource
import com.tenmilelabs.chefai.mealplans.domain.model.VarietyPreference
import java.util.UUID

@Composable
fun MealPlanCard(
    mealPlan: MealPlan,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = mealPlan.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = mealPlan.status)
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete meal plan",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = buildPreferenceSummary(mealPlan),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun StatusBadge(
    status: MealPlanStatus,
    modifier: Modifier = Modifier,
) {
    val (text, color) = when (status) {
        MealPlanStatus.DRAFT -> "📝 Draft" to MaterialTheme.colorScheme.tertiary
        MealPlanStatus.GENERATING -> "⏳ Generating" to MaterialTheme.colorScheme.primary
        MealPlanStatus.READY -> "✅ Ready" to MaterialTheme.colorScheme.primary
        MealPlanStatus.ARCHIVED -> "📦 Archived" to MaterialTheme.colorScheme.outline
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier.padding(end = 4.dp),
    )
}

private val previewPreferences = MealPlanPreferences(
    planLengthDays = 5,
    mealType = MealType.DINNER_AND_LUNCH,
    dietaryRestrictions = setOf(DietaryRestriction.VEGAN),
    recipeSource = RecipeSource.INCLUDE_PUBLIC,
    maxPrepTimeMinutes = 30,
    servingsPerMeal = 4,
    batchCooking = true,
    leftoverFriendly = false,
    varietyPreference = VarietyPreference.HIGH,
)

@Preview(name = "Draft — Light", showBackground = true)
@Composable
private fun MealPlanCardDraftLightPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanCard(
            mealPlan = MealPlan(
                uuid = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                name = "5-day meal plan",
                status = MealPlanStatus.DRAFT,
                preferences = previewPreferences,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                days = emptyList(),
            ),
            onDelete = {},
        )
    }
}

@Preview(name = "Ready — Dark", showBackground = true)
@Composable
private fun MealPlanCardReadyDarkPreview() {
    ChefAITheme(darkTheme = true) {
        MealPlanCard(
            mealPlan = MealPlan(
                uuid = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                name = "7-day batch cook plan",
                status = MealPlanStatus.READY,
                preferences = previewPreferences.copy(
                    planLengthDays = 7,
                    mealType = MealType.DINNER,
                    dietaryRestrictions = setOf(DietaryRestriction.LOW_CARB, DietaryRestriction.GLUTEN_FREE),
                    batchCooking = true,
                    leftoverFriendly = true,
                ),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                days = emptyList(),
            ),
            onDelete = {},
        )
    }
}

@Preview(name = "Generating — Light", showBackground = true)
@Composable
private fun MealPlanCardGeneratingLightPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanCard(
            mealPlan = MealPlan(
                uuid = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                name = "3-day quick plan",
                status = MealPlanStatus.GENERATING,
                preferences = previewPreferences.copy(
                    planLengthDays = 3,
                    mealType = MealType.DINNER,
                    dietaryRestrictions = emptySet(),
                    maxPrepTimeMinutes = 20,
                    servingsPerMeal = 2,
                ),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                days = emptyList(),
            ),
            onDelete = {},
        )
    }
}

private fun buildPreferenceSummary(mealPlan: MealPlan): String {
    val prefs = mealPlan.preferences
    val parts = mutableListOf<String>()

    parts.add("${prefs.mealType.emoji} ${prefs.planLengthDays} days")
    parts.add("👥 ${prefs.servingsPerMeal} servings")

    val dietary = prefs.dietaryRestrictions.filter {
        it != com.tenmilelabs.chefai.mealplans.domain.model.DietaryRestriction.NONE
    }
    if (dietary.isNotEmpty()) {
        parts.add(dietary.joinToString(" ") { it.emoji })
    }

    if (prefs.batchCooking) parts.add("❄️ Batch")
    if (prefs.leftoverFriendly) parts.add("♻️ Leftovers")

    return parts.joinToString(" · ")
}
