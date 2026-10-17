package com.tenmilelabs.chefai.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.PanelMetrics
import com.tenmilelabs.chefai.core.ui.components.RecipeListCard
import com.tenmilelabs.chefai.core.ui.components.panelSegment
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.core.util.LoadingContent
import java.util.UUID

/**
 * The Search tab's search bar. Collapsed it sits at the top of the tab; expanded it takes over the
 * screen and renders results. [expanded] is hoisted so the browse page's category cards can open
 * the results view (see [SearchScreen]). [RecipeSearchViewModel] owns query text and results.
 *
 * The M3 [SearchBar] loses its pill here: the outer surface is zero-radius and blends into the
 * `chefColors.panelGround` page, and the field is a square of `chefColors.panelSurface` — set off
 * by tone, not a border (docs/design/modernist.md § 3.1). Expanded, the icons take the accent.
 *
 * Results sit on one panel, drawn item by item with [panelSegment] since the list is lazy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeSearchBar(
    viewModel: RecipeSearchViewModel,
    snackbarHostState: SnackbarHostState,
    onRecipeClick: (UUID) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is SearchUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(
                    message = context.getString(event.messageRes),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    LaunchedEffect(viewModel, onExpandedChange, onRecipeClick) {
        viewModel.navigateToRecipe.collect { recipeId ->
            onExpandedChange(false)
            onRecipeClick(recipeId)
        }
    }

    val iconColor = if (expanded) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val fieldFill = MaterialTheme.chefColors.panelSurface

    SearchBar(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        shape = RectangleShape,
        // M3 rules the field off from the results when expanded; matching the ground hides it.
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.chefColors.panelGround,
            dividerColor = MaterialTheme.chefColors.panelGround,
        ),
        // The shell's header already sits below the status bar; M3's default insets would pad the
        // bar down by the status bar height a second time.
        windowInsets = WindowInsets(0, 0, 0, 0),
        inputField = {
            // This InputField overload hard-codes M3's pill shape, so the square fill is painted
            // here and the field's own container takes the same color — the pill never shows.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(fieldFill),
            ) {
                SearchBarDefaults.InputField(
                    modifier = Modifier.fillMaxWidth(),
                    query = query,
                    onQueryChange = viewModel::onQueryChanged,
                    onSearch = { onExpandedChange(true) },
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.placeholder_search),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(ChefAIIcons.Search),
                            contentDescription = null,
                            tint = iconColor,
                        )
                    },
                    trailingIcon = {
                        if (expanded) {
                            IconButton(onClick = {
                                onExpandedChange(false)
                                viewModel.onQueryChanged("")
                            }) {
                                Icon(
                                    painter = painterResource(ChefAIIcons.X),
                                    contentDescription = stringResource(R.string.search_clear_content_description),
                                    tint = iconColor,
                                )
                            }
                        }
                    },
                    colors = SearchBarDefaults.inputFieldColors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = fieldFill,
                        unfocusedContainerColor = fieldFill,
                        disabledContainerColor = fieldFill,
                    ),
                )
            }
        },
    ) {
        SearchResultsContent(
            uiState = uiState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onRecipeClick = viewModel::onRecipeClick,
            onSaveToCollection = viewModel::onSaveToCollection,
        )
    }
}

@Composable
private fun ColumnScope.SearchResultsContent(
    uiState: SearchUiState,
    onRecipeClick: (UUID) -> Unit,
    onSaveToCollection: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        SearchUiState.Idle -> Unit

        SearchUiState.Searching -> LoadingContent(modifier = modifier)

        SearchUiState.Empty -> Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.search_empty_results),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.search_empty_results_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        is SearchUiState.Error -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(uiState.messageRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is SearchUiState.Results -> Box(modifier = modifier) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // No side inset: the bar itself is already padded in to the panel edge.
                contentPadding = PaddingValues(bottom = PanelMetrics.Gap),
            ) {
                item(key = "results_count") {
                    Text(
                        text = pluralStringResource(
                            R.plurals.search_results_count,
                            uiState.items.size,
                            uiState.items.size,
                        ).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
                if (uiState.isOffline) {
                    item(key = "offline_banner") {
                        Text(
                            text = stringResource(R.string.search_offline_results),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(items = uiState.items, key = { _, recipe -> recipe.uuid }) { index, recipe ->
                    RecipeListCard(
                        recipe = recipe,
                        isInCollection = recipe.uuid in uiState.bookmarkedRecipeIds,
                        onSaveToCollection = onSaveToCollection,
                        showDivider = false,
                        navigateToDetail = onRecipeClick,
                        modifier = Modifier
                            .panelSegment(
                                isFirst = index == 0,
                                isLast = index == uiState.items.lastIndex,
                            )
                            .padding(horizontal = 12.dp)
                            // Rows already pad 12dp vertically; top up the ends to the panel's 16.
                            .padding(
                                top = if (index == 0) 4.dp else 0.dp,
                                bottom = if (index == uiState.items.lastIndex) 4.dp else 0.dp,
                            ),
                    )
                }
            }
        }
    }
}
