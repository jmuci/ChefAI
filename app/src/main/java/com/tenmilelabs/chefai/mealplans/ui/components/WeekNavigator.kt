package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Prev / next chevrons around a centered date range — "Aug 11 – 17".
 *
 * @param weekStart the first day of the week being read; [weekEnd] is rendered from the same
 *   formatter so a range inside one month reads "Aug 11 – 17" and one crossing a boundary reads
 *   "Aug 28 – Sep 3".
 */
@Composable
fun WeekNavigator(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigatorButton(
            icon = ChefAIIcons.ChevronLeft,
            contentDescription = stringResource(R.string.meal_plans_previous_week),
            onClick = onPreviousWeek,
        )
        Text(
            text = formatRange(weekStart, weekEnd),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        NavigatorButton(
            icon = ChefAIIcons.ChevronRight,
            contentDescription = stringResource(R.string.meal_plans_next_week),
            onClick = onNextWeek,
        )
    }
}

@Composable
private fun NavigatorButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(HitTarget)
            .flatClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(IconSize),
        )
    }
}

/**
 * "Aug 11 – 17" within a month, "Aug 28 – Sep 3" across one.
 *
 * Repeating the month only when it changes is what the design shows, and it is also what makes the
 * label fit between two 44dp buttons on a narrow phone.
 */
private fun formatRange(start: LocalDate, end: LocalDate): String {
    val locale = Locale.getDefault()
    val monthDay = DateTimeFormatter.ofPattern("MMM d", locale)
    val dayOnly = DateTimeFormatter.ofPattern("d", locale)
    val tail = if (start.month == end.month) dayOnly else monthDay
    return "${monthDay.format(start)} – ${tail.format(end)}"
}

private val HitTarget = 44.dp
private val IconSize = 20.dp

@Preview(name = "Week navigator — light")
@Preview(name = "Week navigator — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeekNavigatorPreview() {
    ChefAITheme {
        Column(
            Modifier.background(MaterialTheme.colorScheme.background),
        ) {
            WeekNavigator(
                weekStart = LocalDate.of(2026, 8, 11),
                weekEnd = LocalDate.of(2026, 8, 17),
                onPreviousWeek = {},
                onNextWeek = {},
            )
            WeekNavigator(
                weekStart = LocalDate.of(2026, 8, 28),
                weekEnd = LocalDate.of(2026, 9, 3),
                onPreviousWeek = {},
                onNextWeek = {},
            )
        }
    }
}
