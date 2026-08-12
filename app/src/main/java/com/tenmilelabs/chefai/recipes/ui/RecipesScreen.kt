package com.tenmilelabs.chefai.recipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.UuidV7Generator
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.RecipeListCard
import com.tenmilelabs.chefai.core.ui.components.SectionHeaderWithSubtitle
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import com.tenmilelabs.chefai.core.ui.preview.RecipePreviewProvider
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import java.util.UUID

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
    if (loading) {
        LoadingContent()
    } else {
        if (recipes.isEmpty()) {
            EmptyContent(
                R.string.no_recipes_title,
                R.string.no_recipes_subtitle,
                R.drawable.ic_chef_hat_black_24dp
            )
        } else {
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
                            navigateToDetail = { recipeCardOnClick(recipe.uuid) }
                        )
                    }
                }
            }
        }
    }
}


@Preview
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