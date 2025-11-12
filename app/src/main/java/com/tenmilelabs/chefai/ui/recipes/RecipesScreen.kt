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
import com.tenmilelabs.chefai.data.source.local.util.generateUuid7
import com.tenmilelabs.chefai.domain.model.Label
import com.tenmilelabs.chefai.domain.model.Recipe
import com.tenmilelabs.chefai.domain.model.User
import com.tenmilelabs.chefai.ui.components.RecipeCard
import com.tenmilelabs.chefai.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.util.EmptyContent
import com.tenmilelabs.chefai.util.LoadingContent
import xyz.block.uuidv7.UUIDv7
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
    val previewUser = com.tenmilelabs.chefai.domain.model.User(
        uuid = generateUuid7(),
        displayName = "ChefAI Preview",
        email = "preview@chefai.app",
        avatarUrl = null
    )
    val recipes: List<Recipe> = buildList {
        for (i in 1..60) {
            add(
                Recipe(
                    uuid = generateUuid7(),
                    title = "Mediterranean Grilled Chicken",
                    description = "A light and flavorful grilled chicken recipe with classic Mediterranean herbs and a lemon-garlic marinade.",
                    imageUrl = "https://via.placeholder.com/150",
                    imageUrlThumbnail = "https://via.placeholder.com/150",
                    prepTimeMinutes = 15,
                    cookTimeMinutes = 20,
                    servings = 4,
                    creator = previewUser,
                    recipeExternalUrl = "https://example.com/recipe",
                    ingredients = emptyList(),
                    steps = emptyList(),
                    tags = emptyList(),
                    labels = listOf(Label(generateUuid7(), "Mediterranean")),
                    updatedAt = System.currentTimeMillis()
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
    val previewUser = User(
        uuid = UUID.randomUUID(),
        displayName = "Preview User",
        email = "user@preview.com",
        avatarUrl = null
    )
    ChefAITheme {
        Surface {
            RecipeCard(
                recipe = Recipe(
                    uuid = UUID.randomUUID(),
                    title = "Delicious Grilled Chicken",
                    description = "A very tasty and easy to make grilled chicken recipe. Perfect for a summer barbecue. Follow the steps carefully for the best results.",
                    imageUrl = "https://via.placeholder.com/200",
                    imageUrlThumbnail = "https://via.placeholder.com/200",
                    prepTimeMinutes = 15,
                    cookTimeMinutes = 20,
                    servings = 4,
                    creator = previewUser,
                    recipeExternalUrl = "https://example.com/grilled-chicken",
                    ingredients = emptyList(),
                    steps = emptyList(),
                    tags = emptyList(),
                    labels = listOf(Label(UUID.randomUUID(), "Grill")),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
}