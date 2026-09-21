package com.tenmilelabs.chefai.search.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.tenmilelabs.chefai.core.ui.components.RecipeListCard
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
 * ground, and the visible rectangle is a 2dp border drawn around the input field itself (so it
 * wraps only the field, not the full-screen results underneath it once expanded). Rest is
 * `colorScheme.outline`; expanded takes the accent border and accent icon the handoff specs.
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

    val fieldColor = if (expanded) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    SearchBar(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        shape = RectangleShape,
        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        inputField = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = MaterialTheme.chefColors.sectionRuleWidth, color = fieldColor),
            ) {
                SearchBarDefaults.InputField(
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
                            tint = fieldColor,
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
                                    tint = fieldColor,
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
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        disabledContainerColor = MaterialTheme.colorScheme.background,
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "results_count") {
                    Text(
                        text = pluralStringResource(
                            R.plurals.search_results_count,
                            uiState.items.size,
                            uiState.items.size,
                        ).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                if (uiState.isOffline) {
                    item(key = "offline_banner") {
                        Text(
                            text = stringResource(R.string.search_offline_results),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(items = uiState.items, key = { it.uuid }) { recipe ->
                    RecipeListCard(
                        recipe = recipe,
                        isInCollection = recipe.uuid in uiState.bookmarkedRecipeIds,
                        onSaveToCollection = onSaveToCollection,
                        navigateToDetail = onRecipeClick,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}
