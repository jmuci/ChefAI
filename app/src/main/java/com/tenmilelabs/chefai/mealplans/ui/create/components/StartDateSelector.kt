package com.tenmilelabs.chefai.mealplans.ui.create.components

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.mealplans.ui.components.MealPlanDatePickerDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "Which day does the meal plan start?" — a row showing the chosen date that opens a calendar.
 *
 * [startDate] is `null` only while the wizard is still reading the default; the row shows a dash
 * rather than a date that would jump a frame later. Past days are not selectable: a plan is
 * something to cook from now on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartDateSelector(
    startDate: LocalDate?,
    onStartDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        WizardSectionLabel(stringResource(R.string.wizard_start_date_title))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .flatClickable(
                    onClick = { showPicker = true },
                    enabled = startDate != null,
                    role = Role.Button,
                )
                .border(
                    width = MaterialTheme.chefColors.sectionRuleWidth,
                    color = MaterialTheme.colorScheme.outline,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = startDate?.format(DateFormatter) ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = stringResource(R.string.wizard_start_date_pick),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (showPicker && startDate != null) {
        MealPlanDatePickerDialog(
            initialDate = startDate,
            today = LocalDate.now(),
            title = null,
            onConfirm = onStartDateSelected,
            onDismiss = { showPicker = false },
        )
    }
}

private val DateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

private val RowHeight = 52.dp

@Preview(name = "Start date — light")
@Preview(name = "Start date — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StartDateSelectorPreview() {
    ChefAITheme {
        StartDateSelector(
            startDate = LocalDate.of(2026, 10, 29),
            onStartDateSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
