package com.tenmilelabs.chefai.mealplans.ui.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.RecipeListCard
import com.tenmilelabs.chefai.core.ui.components.flat.FlatChip
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

/**
 * The "+ Add meal" sheet: which meal of [date] this is for, then the recipe — picked from the
 * user's saved [recipes] (filtered by [query]) right here, or created via "Add a New Recipe".
 *
 * Opens straight to a fixed ~80% of the screen rather than M3's 50% initial cap, so there is room
 * for results above the keyboard. Only the results list scrolls; the header (title, slot, search
 * field, "Add a New Recipe") stays pinned. The sheet's built-in nested scroll means a downward
 * drag at the top of the list moves the sheet and dismisses it.
 *
 * The slot is a sheet-local choice (dinner by default, kept across rotation) because it is
 * meaningless until a recipe is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealSheet(
    date: LocalDate,
    query: String,
    recipes: List<RecipePreview>,
    onQueryChange: (String) -> Unit,
    onRecipePicked: (MealSlot, UUID) -> Unit,
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
            query = query,
            recipes = recipes,
            onQueryChange = onQueryChange,
            onRecipePicked = onRecipePicked,
            onNewRecipe = onNewRecipe,
            modifier = Modifier.fillMaxHeight(SheetHeightFraction),
        )
    }
}

@Composable
private fun AddMealSheetContent(
    date: LocalDate,
    query: String,
    recipes: List<RecipePreview>,
    onQueryChange: (String) -> Unit,
    onRecipePicked: (MealSlot, UUID) -> Unit,
    onNewRecipe: (MealSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    var slot by rememberSaveable { mutableStateOf(MealSlot.DINNER) }

    // The sheet's default insets stop at the navigation bar; the keyboard is ours to clear, or it
    // would cover the results the user is typing to find.
    Column(modifier = modifier.navigationBarsPadding().imePadding()) {
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
        FlatField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.meal_plans_add_meal_search),
            placeholder = stringResource(R.string.meal_plans_add_meal_search_placeholder),
            leadingIcon = ChefAIIcons.Search,
            trailing = if (query.isNotEmpty()) {
                { ClearQueryButton(onClick = { onQueryChange("") }) }
            } else {
                null
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        RowRule()
        SheetOption(
            icon = ChefAIIcons.Plus,
            label = stringResource(R.string.meal_plans_add_meal_new),
            onClick = { onNewRecipe(slot) },
        )
        RowRule()
        if (recipes.isEmpty()) {
            Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.meal_plans_add_meal_no_saved)
                } else {
                    stringResource(R.string.meal_plans_add_meal_no_match, query.trim())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                items(recipes, key = { it.uuid }) { recipe ->
                    RecipeListCard(
                        recipe = recipe,
                        navigateToDetail = { onRecipePicked(slot, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClearQueryButton(onClick: () -> Unit) {
    Icon(
        painter = painterResource(ChefAIIcons.X),
        contentDescription = stringResource(R.string.search_clear_content_description),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(FieldIconSize)
            .flatClickable(onClick = onClick, role = Role.Button),
    )
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

/** The user asked for the sheet to cover roughly 80% of the screen. */
private const val SheetHeightFraction = 0.8f

private val FieldIconSize = 17.dp

private fun previewRecipe(title: String, minutes: Int) = RecipePreview(
    uuid = UUID.nameUUIDFromBytes(title.toByteArray()),
    title = title,
    description = "",
    imageUrlThumbnail = "",
    prepTimeMinutes = minutes,
    cookTimeMinutes = 0,
    servings = 2,
    creatorId = UUID(0, 0),
    tags = emptyList(),
    labels = emptyList(),
)

@Preview(name = "Add meal sheet — light")
@Preview(name = "Add meal sheet — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddMealSheetContentPreview() {
    ChefAITheme {
        AddMealSheetContent(
            date = LocalDate.of(2026, 10, 29),
            query = "",
            recipes = listOf(
                previewRecipe("Margherita Pizza", 35),
                previewRecipe("Tofu Veggie Bowl", 27),
                previewRecipe("Honey Teriyaki Salmon", 45),
            ),
            onQueryChange = {},
            onRecipePicked = { _, _ -> },
            onNewRecipe = {},
        )
    }
}

@Preview(name = "Add meal sheet, no match — light")
@Preview(name = "Add meal sheet, no match — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddMealSheetContentNoMatchPreview() {
    ChefAITheme {
        AddMealSheetContent(
            date = LocalDate.of(2026, 10, 29),
            query = "lasagne",
            recipes = emptyList(),
            onQueryChange = {},
            onRecipePicked = { _, _ -> },
            onNewRecipe = {},
        )
    }
}

@Preview(name = "Add meal sheet, nothing saved — light")
@Preview(name = "Add meal sheet, nothing saved — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddMealSheetContentEmptyPreview() {
    ChefAITheme {
        AddMealSheetContent(
            date = LocalDate.of(2026, 10, 29),
            query = "",
            recipes = emptyList(),
            onQueryChange = {},
            onRecipePicked = { _, _ -> },
            onNewRecipe = {},
        )
    }
}
