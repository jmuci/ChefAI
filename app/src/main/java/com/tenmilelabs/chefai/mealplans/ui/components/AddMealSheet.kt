package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatChip
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The "+ Add meal" sheet: which meal of [date] this is for, then how to find the recipe.
 *
 * Both rows are terminal — they hand back the chosen [MealSlot] and the caller navigates to the
 * recipe picker or the importer/editor. The slot is a sheet-local choice (dinner by default,
 * kept across rotation) because it is meaningless until a row is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealSheet(
    date: LocalDate,
    onSearchRecipe: (MealSlot) -> Unit,
    onNewRecipe: (MealSlot) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        AddMealSheetContent(
            date = date,
            onSearchRecipe = onSearchRecipe,
            onNewRecipe = onNewRecipe,
        )
    }
}

@Composable
private fun AddMealSheetContent(
    date: LocalDate,
    onSearchRecipe: (MealSlot) -> Unit,
    onNewRecipe: (MealSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    var slot by rememberSaveable { mutableStateOf(MealSlot.DINNER) }

    Column(modifier = modifier.navigationBarsPadding()) {
        Text(
            text = stringResource(
                R.string.meal_plans_add_meal_sheet_title,
                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())),
            ),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MealSlot.entries.forEach { option ->
                FlatChip(
                    label = option.label,
                    selected = option == slot,
                    onClick = { slot = option },
                    singleSelect = true,
                )
            }
        }
        RowRule()
        SheetOption(
            icon = ChefAIIcons.Search,
            label = stringResource(R.string.meal_plans_add_meal_search),
            onClick = { onSearchRecipe(slot) },
        )
        RowRule()
        SheetOption(
            icon = ChefAIIcons.Plus,
            label = stringResource(R.string.meal_plans_add_meal_new),
            onClick = { onNewRecipe(slot) },
        )
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun SheetOption(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .flatClickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(name = "Add meal sheet — light")
@Preview(name = "Add meal sheet — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddMealSheetContentPreview() {
    ChefAITheme {
        AddMealSheetContent(
            date = LocalDate.of(2026, 10, 29),
            onSearchRecipe = {},
            onNewRecipe = {},
        )
    }
}
