package com.tenmilelabs.chefai.search.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.search.ui.components.SearchBrowseContent
import java.util.UUID

/**
 * The Search tab. A [RecipeSearchBar] overlays the browse landing page; expanding it (by typing or
 * by tapping a category card) covers the page with results. Mirrors how the bar sat on Home before
 * search became its own tab.
 */
@Composable
fun SearchScreen(
    snackbarHostState: SnackbarHostState,
    onRecipeClick: (UUID) -> Unit,
    viewModel: RecipeSearchViewModel = hiltViewModel(),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        SearchBrowseContent(
            onCategoryClick = { /* Step 6 */ },
            contentPadding = PaddingValues(
                start = dimensionResource(R.dimen.padding_medium),
                end = dimensionResource(R.dimen.padding_medium),
                top = dimensionResource(R.dimen.search_bar_clearance),
                bottom = dimensionResource(R.dimen.padding_medium),
            ),
        )

        RecipeSearchBar(
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onRecipeClick = onRecipeClick,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(R.dimen.padding_small)),
        )
    }
}
