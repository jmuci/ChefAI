package com.tenmilelabs.chefai.recipes.ui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.flat.FlatChip
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import com.tenmilelabs.chefai.core.ui.preview.RecipePreviewProvider
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.recipes.ui.components.RecipeGridCard
import java.util.UUID

/**
 * The filter chips above the recipe grid (04). Only [ALL] is wired to anything today — the others
 * render selectable but inert. See the GitHub issue filed alongside this screen's redesign for what
 * real filtering needs: where the state lives, how it maps to tags/labels, and how it interacts
 * with the search pipeline.
 */
private enum class RecipeFilter(@StringRes val labelRes: Int) {
    ALL(R.string.recipe_filter_all),
    QUICK_EASY(R.string.search_category_quick_easy),
    VEGETARIAN(R.string.search_category_vegetarian),
    SEAFOOD(R.string.recipe_filter_seafood),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onRecipeCardClick: (UUID) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    PullToRefreshBox(
        modifier = Modifier.testTag("RecipesScreen"),
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::onRefresh,
    ) {
        RecipesContent(
            loading = uiState.isLoading,
            recipes = uiState.items,
            recipeCardOnClick = onRecipeCardClick,
        )
    }

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
    loading: Boolean,
    recipes: List<RecipePreview>,
    recipeCardOnClick: (UUID) -> Unit = {},
) {
    var selectedFilter by rememberSaveable { mutableStateOf(RecipeFilter.ALL) }

    if (loading) {
        LoadingContent()
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            FilterChipsRow(
                selected = selectedFilter,
                onSelect = { selectedFilter = it },
            )

            if (recipes.isEmpty()) {
                EmptyContent(
                    R.string.no_recipes_title,
                    R.string.no_recipes_subtitle,
                    R.drawable.ic_chef_hat_black_24dp
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(items = recipes, key = { it.uuid }) { recipe ->
                        RecipeGridCard(
                            recipe = recipe,
                            onClick = recipeCardOnClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selected: RecipeFilter,
    onSelect: (RecipeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lazyRowItems(items = RecipeFilter.entries) { filter ->
            FlatChip(
                label = stringResource(filter.labelRes),
                selected = filter == selected,
                onClick = { onSelect(filter) },
                singleSelect = true,
            )
        }
    }
}


@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RecipesListScreenPreview() {
    // Create unique recipe previews for the list
    val recipes: List<RecipePreview> = buildList {
        val baseList = PreviewData.recipePreviewList
        for (i in 0 until 60) {
            val baseRecipe = baseList[i % baseList.size]
            // Create a copy with a unique UUID for each item
            add(baseRecipe.copy(uuid = UuidV7Generator.newId()))
        }
    }
    ChefAITheme {
        Surface {
            RecipesContent(false, recipes)
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RecipesListScreenEmptyPreview() {
    val recipes: List<RecipePreview> = emptyList()
    ChefAITheme {
        Surface {
            RecipesContent(false, recipes)
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RecipeGridCardScreenPreview(
    @PreviewParameter(RecipePreviewProvider::class) recipe: RecipePreview
) {
    ChefAITheme {
        Surface {
            RecipeGridCard(recipe = recipe, onClick = {})
        }
    }
}
