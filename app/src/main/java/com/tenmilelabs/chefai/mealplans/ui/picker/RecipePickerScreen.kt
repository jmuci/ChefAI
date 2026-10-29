package com.tenmilelabs.chefai.mealplans.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatChip
import com.tenmilelabs.chefai.recipes.ui.RecipesScreen
import com.tenmilelabs.chefai.search.ui.SearchScreen
import java.util.UUID

private enum class PickerSource { MINE, DISCOVER }

/**
 * Where "+ Add meal → Search for a Recipe" lands: the user's own recipes, or the public catalog.
 *
 * Two sources because neither alone is "search for a recipe": the catalog search never returns the
 * user's private recipes (URL imports default to Private), and the library alone cannot reach
 * anything they have not saved. Each tab is the existing screen with its row tap redirected — a
 * tap picks the recipe instead of opening it. Search has already made sure a catalog recipe exists
 * on the device by the time it reports the tap, so [onRecipePicked] only ever sees local ids.
 */
@Composable
fun RecipePickerScreen(
    snackbarHostState: SnackbarHostState,
    onRecipePicked: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    var source by rememberSaveable { mutableStateOf(PickerSource.MINE) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            FlatChip(
                label = stringResource(R.string.recipe_picker_tab_mine),
                selected = source == PickerSource.MINE,
                onClick = { source = PickerSource.MINE },
                singleSelect = true,
            )
            FlatChip(
                label = stringResource(R.string.recipe_picker_tab_discover),
                selected = source == PickerSource.DISCOVER,
                onClick = { source = PickerSource.DISCOVER },
                singleSelect = true,
            )
        }
        when (source) {
            PickerSource.MINE -> RecipesScreen(
                snackbarHostState = snackbarHostState,
                onRecipeCardClick = onRecipePicked,
            )
            PickerSource.DISCOVER -> SearchScreen(
                snackbarHostState = snackbarHostState,
                onRecipeClick = onRecipePicked,
            )
        }
    }
}
