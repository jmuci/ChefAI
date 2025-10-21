package com.tenmilelabs.chefai.ui.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.data.source.local.util.generateUuid7
import com.tenmilelabs.chefai.domain.model.RecipePreview
import com.tenmilelabs.chefai.ui.components.RecipeListCard
import com.tenmilelabs.chefai.ui.preview.PreviewData
import com.tenmilelabs.chefai.ui.preview.RecipePreviewProvider
import com.tenmilelabs.chefai.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.util.EmptyContent
import com.tenmilelabs.chefai.util.LoadingContent
import java.util.UUID

@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onRecipeCardClick: (UUID) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecipesContent(
        loading = uiState.isLoading,
        recipes = uiState.items,
        recipeCardOnClick = onRecipeCardClick,
    )

    // Check for user messages to display on the screen
    uiState.userMessage?.let { message ->
        val snackBarText = stringResource(message)
        LaunchedEffect(snackbarHostState, viewModel, message, snackBarText) {
            snackbarHostState.showSnackbar(
                message = snackBarText,
                duration = SnackbarDuration.Short
            )
            viewModel.snackbarMessageShown()
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
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimensionResource(id = R.dimen.padding_medium)),
        ) {
            LazyColumn {
                items(recipes) { recipe ->
                    RecipeListCard(
                        recipe = recipe,
                        navigateToDetail = { recipeCardOnClick(recipe.uuid) })
                }
            }
        }
    }
}


@Preview
@Composable
fun RecipesListScreenPreview() {
    val previewUser = com.tenmilelabs.chefai.domain.model.User(
        uuid = generateUuid7(),
        displayName = "ChefAI Preview",
        email = "preview@chefai.app",
        avatarUrl = null
    )
    val recipes: List<RecipePreview> = buildList {
        for (i in 1..60) {
            add(PreviewData.recipePreview)
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