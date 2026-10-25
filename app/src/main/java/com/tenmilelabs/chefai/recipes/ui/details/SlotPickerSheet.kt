package com.tenmilelabs.chefai.recipes.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.util.UUID

/**
 * Two-step picker: a plan, then a day + meal within it. Only [RecipeDetailsAction.SlotPicked] and
 * [RecipeDetailsAction.SlotPickerDismissed] are ever sent to [onAction].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotPickerSheet(
    state: SlotPickerUiState,
    onAction: (RecipeDetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPlan = state.plans.firstOrNull { it.uuid.toString() == selectedPlanId }

    ModalBottomSheet(
        onDismissRequest = { onAction(RecipeDetailsAction.SlotPickerDismissed) },
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(
                    when (state.purpose) {
                        SlotPickerPurpose.MEAL_PLAN -> R.string.slot_picker_meal_plan_title
                        SlotPickerPurpose.GROCERY_LIST -> R.string.slot_picker_grocery_list_title
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            when {
                state.plans.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.slot_picker_no_plans),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    CreatePlanRow { onAction(RecipeDetailsAction.CreatePlanWithRecipeClicked) }
                }
                selectedPlan == null -> PlanList(
                    plans = state.plans,
                    onPlanClick = { selectedPlanId = it.uuid.toString() },
                    onCreatePlanClick = { onAction(RecipeDetailsAction.CreatePlanWithRecipeClicked) },
                )
                else -> {
                    TextButton(onClick = { selectedPlanId = null }) {
                        Text(stringResource(R.string.slot_picker_back))
                    }
                    SlotList(
                        plan = selectedPlan,
                        onSlotClick = { dayIndex, slot ->
                            onAction(RecipeDetailsAction.SlotPicked(selectedPlan.uuid, dayIndex, slot))
                        },
                        onNewDayClick = { slot ->
                            onAction(RecipeDetailsAction.NewDayPicked(selectedPlan.uuid, slot))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanList(
    plans: List<MealPlan>,
    onPlanClick: (MealPlan) -> Unit,
    onCreatePlanClick: () -> Unit,
) {
    Text(stringResource(R.string.slot_picker_choose_plan), style = MaterialTheme.typography.labelLarge)
    LazyColumn {
        items(plans, key = { it.uuid }) { plan ->
            Text(
                text = plan.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlanClick(plan) }
                    .padding(vertical = 14.dp),
            )
        }
        item(key = "create_plan") { CreatePlanRow(onCreatePlanClick) }
    }
}

@Composable
private fun CreatePlanRow(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.slot_picker_create_plan),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun SlotList(
    plan: MealPlan,
    onSlotClick: (dayIndex: Int, slot: MealSlot) -> Unit,
    onNewDayClick: (slot: MealSlot) -> Unit,
) {
    Text(stringResource(R.string.slot_picker_choose_slot), style = MaterialTheme.typography.labelLarge)
    LazyColumn {
        items((0 until plan.preferences.planLengthDays).toList(), key = { it }) { dayIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.slot_picker_day, dayIndex + 1),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp),
                )
                MealSlot.entries.forEach { slot ->
                    TextButton(onClick = { onSlotClick(dayIndex, slot) }) { Text(slot.label) }
                }
            }
        }
        item(key = "new_day") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.slot_picker_add_new_day),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp),
                )
                MealSlot.entries.forEach { slot ->
                    TextButton(onClick = { onNewDayClick(slot) }) { Text(slot.label) }
                }
            }
        }
    }
}
