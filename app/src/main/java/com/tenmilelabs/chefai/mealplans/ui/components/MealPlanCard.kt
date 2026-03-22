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
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlanStatus

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
