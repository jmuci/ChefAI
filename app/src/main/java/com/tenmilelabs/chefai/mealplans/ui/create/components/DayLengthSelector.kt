package com.tenmilelabs.chefai.mealplans.ui.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.flatSelectable
import com.tenmilelabs.chefai.core.ui.theme.chefColors

private val DayOptions = listOf(3, 5, 7)

@Composable
fun DayLengthSelector(
    selectedDays: Int,
    onDaysSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WizardSectionLabel(stringResource(R.string.wizard_plan_length_title))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayOptions.forEach { days ->
                DayCell(
                    days = days,
                    selected = selectedDays == days,
                    onClick = { onDaysSelected(days) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    days: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressedTint = if (selected) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.chefColors.neutral.s200
    }

    Box(
        modifier = modifier
            .height(CellHeight)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
            .flatSelectable(selected = selected, onClick = onClick, pressedTint = pressedTint)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(
                        width = MaterialTheme.chefColors.sectionRuleWidth,
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = days.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

private val CellHeight = 52.dp
