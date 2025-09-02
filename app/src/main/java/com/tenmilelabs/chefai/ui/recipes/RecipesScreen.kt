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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.data.Recipe
import com.tenmilelabs.chefai.ui.components.RecipeCard
import com.tenmilelabs.chefai.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.util.EmptyContent
import com.tenmilelabs.chefai.util.LoadingContent

@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onRecipeCardClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecipesContent(
        loading = uiState.isLoading,
        recipes = uiState.items,
        recipeCardOnClick = onRecipeCardClick,
    )

    // Check for user messages to display on the screen
    uiState.userMessage?.let { message ->
        val snackbarText = stringResource(message)
        LaunchedEffect(snackbarHostState, viewModel, message, snackbarText) {
            snackbarHostState.showSnackbar(
                message = snackbarText,
                duration = SnackbarDuration.Short
            )
            viewModel.snackbarMessageShown()
        }
    }
}

@Composable
fun RecipesContent(
    loading: Boolean,
    recipes: List<Recipe>,
    recipeCardOnClick: (String) -> Unit = {},
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
                    RecipeCard(
                        recipe = recipe,
                        navigateToDetail = { recipeCardOnClick(recipe.uuid) })
                }
            }
        }
    }
}


@Preview
@Composable
fun RecipessListScreenPreview() {
    val recipes: List<Recipe> = buildList {
        for (i in 1..60) {
            add(
                Recipe(
                    title = "Recipe Title $i",
                    label = "Recipe Label $i",
                    description = "Recipe Description $i.  This is how you do this. Follow exactly the following steps to achieve sucesss. \n No cutting corners.",
                    prepTime = 10,
                    recipeUrl = "https://www.google.com/$i",
                    imageUrl = "https://www.google.com",
                    thumbnailUrl = "https://www.google.com",
                )
            )
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
    val recipes: List<Recipe> = emptyList()
    ChefAITheme {
        Surface {
            RecipesContent(false, recipes)
        }
    }
}

@Preview
@Composable
fun RecipeCardPreview() {
    ChefAITheme {
        Surface {
            RecipeCard(
                recipe = Recipe(
                    title = "Recipe Title",
                    label = "Recipe Label",
                    description = "Recipe Description. This is how you do this. Follow exactly the following steps to achieve sucesss. \n No cutting corners.",
                    prepTime = 10,
                    recipeUrl = "https://www.google.com",
                    imageUrl = "https://www.google.com",
                    thumbnailUrl = "https://www.google.com",
                )
            )
        }
    }
}