package com.tenmilelabs.chefai.search.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.RecipeListCard
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import java.util.UUID

/**
 * Home's search bar. Owns its own expanded/collapsed state locally — deliberately not lifted into
 * [com.tenmilelabs.chefai.home.ui.HomeViewModel], which stays untouched. [RecipeSearchViewModel]
 * (via its own [hiltViewModel]) owns only query text and results; neither ViewModel knows the
 * other exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeSearchOverlay(
    snackbarHostState: SnackbarHostState,
    onRecipeClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
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

    SearchBar(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = viewModel::onQueryChanged,
                onSearch = { expanded = true },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                placeholder = { Text(stringResource(R.string.placeholder_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (expanded) {
                        IconButton(onClick = {
                            expanded = false
                            viewModel.onQueryChanged("")
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear_content_description),
                            )
                        }
                    }
                },
            )
        },
    ) {
        SearchResultsContent(
            uiState = uiState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onRecipeClick = { recipeId ->
                expanded = false
                onRecipeClick(recipeId)
            },
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

        SearchUiState.Empty -> EmptyContent(
            title = R.string.search_empty_results,
            subtitle = R.string.search_empty_results_subtitle,
            noRecipesIconRes = R.drawable.ic_chef_hat_black_24dp,
            modifier = modifier,
        )

        is SearchUiState.Error -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(uiState.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is SearchUiState.Results -> Box(modifier = modifier) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (uiState.isOffline) {
                    item(key = "offline_banner") {
                        Text(
                            text = stringResource(R.string.search_offline_results),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(dimensionResource(R.dimen.padding_small)),
                        )
                    }
                }
                items(items = uiState.items, key = { it.uuid }) { recipe ->
                    RecipeListCard(
                        recipe = recipe,
                        isInCollection = recipe.uuid in uiState.bookmarkedRecipeIds,
                        onSaveToCollection = onSaveToCollection,
                        navigateToDetail = onRecipeClick,
                    )
                }
            }
        }
    }
}
