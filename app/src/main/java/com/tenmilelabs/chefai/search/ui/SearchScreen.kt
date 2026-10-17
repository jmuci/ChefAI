package com.tenmilelabs.chefai.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tenmilelabs.chefai.core.ui.components.PanelMetrics
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.search.ui.components.SearchBrowseContent
import java.util.UUID

/**
 * The Search tab. A [RecipeSearchBar] overlays the browse landing page; expanding it (by typing or
 * by tapping a category card) covers the page with results. Mirrors how the bar sat on Home before
 * search became its own tab.
 *
 * Paneled (docs/design/modernist.md § 3.1): the tab's ground is `chefColors.panelGround`.
 */
@Composable
fun SearchScreen(
    snackbarHostState: SnackbarHostState,
    onRecipeClick: (UUID) -> Unit,
    viewModel: RecipeSearchViewModel = hiltViewModel(),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var searchBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.chefColors.panelGround),
    ) {
        SearchBrowseContent(
            onCategoryClick = { category ->
                viewModel.onQueryChanged(category.query)
                expanded = true
            },
            contentPadding = PaddingValues(
                start = PanelMetrics.Inset,
                end = PanelMetrics.Inset,
                top = searchBarHeight + PanelMetrics.Gap,
                bottom = PanelMetrics.Gap,
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
                .padding(horizontal = PanelMetrics.Inset)
                .onSizeChanged { size ->
                    if (!expanded) {
                        searchBarHeight = with(density) { size.height.toDp() }
                    }
                },
        )
    }
}
