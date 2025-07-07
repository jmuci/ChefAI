package com.tenmilelabs.chefai.ui.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.data.Recipe
import com.tenmilelabs.chefai.ui.components.RecipeCard
import com.tenmilelabs.chefai.ui.components.RecipeTimeAndLabelRow
import com.tenmilelabs.chefai.ui.theme.ChefAITheme

@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecipesContent(
        loading = uiState.isLoading,
        recipes = uiState.items,
    )

    // Check for user messages to display on the screen
    uiState.userMessage?.let { message ->
        val snackbarText = stringResource(message)
        LaunchedEffect(snackbarHostState, viewModel, message, snackbarText) {
            snackbarHostState.showSnackbar(snackbarText)
            viewModel.snackbarMessageShown()
        }
    }

}

@Composable
fun RecipesContent(
    loading: Boolean,
    recipes: List<Recipe>,
    modifier: Modifier = Modifier,
) {
    var selectedRecipe by rememberSaveable { mutableStateOf("") }

    if (selectedRecipe.isNotEmpty()) {
        RecipeDetailsFullScreen(recipes.first { it.uuid == selectedRecipe })
    } else {
        //TODO Add loading state
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimensionResource(id = R.dimen.padding_medium)),
        ) {
            Text(
                text = "My Recipes",
                style = MaterialTheme.typography.headlineLarge
            )
            LazyColumn {
                items(recipes) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        navigateToDetail = { selectedRecipe = recipe.uuid })
                }
            }
        }
    }
}

@Composable
fun RecipeDetailsFullScreen(
    recipe: Recipe
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(id = R.dimen.padding_medium)),
    ) {
        Text(
            text = recipe.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        AsyncImage(
            model = recipe.imageUrl,
            placeholder = painterResource(R.drawable.ic_img_placeholder),
            error = painterResource(R.drawable.ic_img_error),
            contentDescription = stringResource(R.string.recipe_image_content_description),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .padding(vertical = dimensionResource(id = R.dimen.padding_small))
                .height(200.dp)
        )
        RecipeTimeAndLabelRow(recipe.prepTime, recipe.label)
        Text(
            text = stringResource(R.string.recipe_steps),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = recipe.description,
        )
        Text(
            buildAnnotatedString {
                withLink(
                    LinkAnnotation.Url(
                        recipe.recipeUrl,
                        TextLinkStyles(style = SpanStyle(color = Color.Blue))
                    )
                ) {
                    append(stringResource(R.string.recipe_hyperlink))
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsFullScreenPreview() {
    ChefAITheme {
        RecipeDetailsFullScreen(
            Recipe(
                title = "Recipe Title",
                label = "Recipe Label",
                description = "Recipe Description.  This is how you do this. Follow exactly the following steps to achieve sucesss. \n No cutting corners.",
                prepTime = 10,
                recipeUrl = "https://www.google.com/",
                imageUrl = "https://www.google.com",
                thumbnailUrl = "https://www.google.com",
            )
        )
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