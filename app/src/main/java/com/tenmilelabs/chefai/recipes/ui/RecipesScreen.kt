package com.tenmilelabs.chefai.recipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.RecipeListCard
import com.tenmilelabs.chefai.core.ui.components.SectionHeaderWithSubtitle
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import com.tenmilelabs.chefai.core.ui.preview.RecipePreviewProvider
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import java.util.UUID

@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onRecipeCardClick: (UUID) -> Unit = {},
) {
    val context = LocalContext.current
    val recipes: LazyPagingItems<RecipePreview> = viewModel.recipesPagingFlow.collectAsLazyPagingItems()

    RecipesContent(
        recipes = recipes,
        recipeCardOnClick = onRecipeCardClick,
        onLoadError = { viewModel.onLoadError() }
    )

    // Collect and handle UI events
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is RecipesUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(event.message),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }
}

@Composable
fun RecipesContent(
    recipes: LazyPagingItems<RecipePreview>,
    recipeCardOnClick: (UUID) -> Unit = {},
    onLoadError: () -> Unit = {},
) {
    // Handle initial loading state
    when (val refreshState = recipes.loadState.refresh) {
        is LoadState.Loading -> {
            LoadingContent()
            return
        }
        is LoadState.Error -> {
            // Notify ViewModel of error so it can emit event
            LaunchedEffect(refreshState.error) {
                onLoadError()
            }
            ErrorContent(
                onRetry = { recipes.retry() }
            )
            return
        }
        else -> Unit
    }

    // Handle empty state
    if (recipes.itemCount == 0) {
        EmptyContent(
            R.string.no_recipes_title,
            R.string.no_recipes_subtitle,
            R.drawable.ic_chef_hat_black_24dp
        )
        return
    }

    // Display recipes
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimensionResource(id = R.dimen.padding_extra_small))
    ) {
        SectionHeaderWithSubtitle("Your Recipes", "All your favorite recipes in one place!")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = dimensionResource(id = R.dimen.padding_extra_small),
                vertical = dimensionResource(id = R.dimen.padding_medium)
            ),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(id = R.dimen.padding_small)
            )
        ) {
            items(
                count = recipes.itemCount,
                key = recipes.itemKey { it.uuid }
            ) { index ->
                // recipes doesn't contain a list of recipes, it loads them lazily as they get requested.
                // The itemCount API is designed with this items API in mind.
                // So we need to check if the item is null before proceeding.
                val recipe = recipes[index]
                if (recipe != null) {
                    RecipeListCard(
                        recipe = recipe,
                        navigateToDetail = { recipeCardOnClick(recipe.uuid) }
                    )
                }
            }

            // Loading indicator for pagination (loading next page)
            if (recipes.loadState.append is LoadState.Loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensionResource(id = R.dimen.padding_medium)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Error indicator for pagination
            when (val appendState = recipes.loadState.append) {
                is LoadState.Error -> {
                    item {
                        // Notify ViewModel of append error
                        LaunchedEffect(appendState.error) {
                            onLoadError()
                        }
                        PagingErrorItem(
                            onRetry = { recipes.retry() }
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ErrorContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_small))
        ) {
            Text(
                text = "Failed to load recipes",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun PagingErrorItem(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimensionResource(id = R.dimen.padding_medium)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_extra_small))
        ) {
            Text(
                text = "Error loading more recipes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview
@Composable
fun RecipesListScreenPreview() {
    // Note: Paging previews are complex. For preview, you'd typically use:
    // 1. A fake PagingData or
    // 2. Preview the content directly with a list
    val recipes: List<RecipePreview> = buildList {
        val baseList = PreviewData.recipePreviewList
        for (i in 0 until 60) {
            val baseRecipe = baseList[i % baseList.size]
            add(baseRecipe.copy(uuid = UUID.randomUUID()))
        }
    }

    ChefAITheme {
        Surface {
            // For preview purposes, we'll show the old non-paging version
            // In production, this would use the paging version
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimensionResource(id = R.dimen.padding_extra_small))
            ) {
                SectionHeaderWithSubtitle("Your Recipes", "All your favorite recipes in one place!")

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = dimensionResource(id = R.dimen.padding_extra_small),
                        vertical = dimensionResource(id = R.dimen.padding_medium)
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        dimensionResource(id = R.dimen.padding_small)
                    )
                ) {
                    items(items = recipes, key = { it.uuid }) { recipe ->
                        RecipeListCard(
                            recipe = recipe,
                            navigateToDetail = { }
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun RecipesListScreenEmptyPreview() {
    ChefAITheme {
        Surface {
            EmptyContent(
                R.string.no_recipes_title,
                R.string.no_recipes_subtitle,
                R.drawable.ic_chef_hat_black_24dp
            )
        }
    }
}

@Preview
@Composable
fun RecipeListCardPreview(
    @PreviewParameter(RecipePreviewProvider::class) recipe: RecipePreview
) {
    ChefAITheme {
        Surface {
            RecipeListCard(recipe)
        }
    }
}

@Preview
@Composable
fun ErrorContentPreview() {
    ChefAITheme {
        Surface {
            ErrorContent(onRetry = {})
        }
    }
}

@Preview
@Composable
fun PagingErrorItemPreview() {
    ChefAITheme {
        Surface {
            PagingErrorItem(onRetry = {})
        }
    }
}