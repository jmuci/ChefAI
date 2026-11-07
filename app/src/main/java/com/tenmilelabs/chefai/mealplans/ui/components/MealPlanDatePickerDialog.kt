package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A calendar dialog for picking a meal plan's start date. Days before [today] are not selectable:
 * a plan is something to cook from now on.
 *
 * @param title optional heading shown above the calendar; `null` keeps the picker's default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanDatePickerDialog(
    initialDate: LocalDate,
    today: LocalDate,
    title: String?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis.toPickerDate() >= today

            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onConfirm(it.toPickerDate()) }
                    onDismiss()
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    ) {
        DatePicker(
            state = pickerState,
            title = title?.let { { Text(it) } },
        )
    }
}

// The M3 date picker speaks UTC-midnight millis for a calendar date, whatever the device zone.
private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Preview(name = "Date picker dialog — light")
@Preview(name = "Date picker dialog — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MealPlanDatePickerDialogPreview() {
    ChefAITheme {
        MealPlanDatePickerDialog(
            initialDate = LocalDate.of(2026, 10, 29),
            today = LocalDate.of(2026, 10, 1),
            title = "Pick a start date",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
