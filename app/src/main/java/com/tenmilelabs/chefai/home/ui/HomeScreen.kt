package com.tenmilelabs.chefai.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.LargeCard
import com.tenmilelabs.chefai.core.ui.components.SectionHeaderWithSubtitle
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import java.util.UUID

/**
 * Home screen with horizontally scrolling recipe carousels.
 * Uses LazyColumn for efficient scrolling of multiple horizontal rows.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onRecipeClick: (UUID) -> Unit = {}
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current

    // Collect and handle UI events
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is HomeUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(event.message),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .testTag("HomeScreen")
            .fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimensionResource(id = R.dimen.padding_extra_small))
    ) {
        // Recipe Suggestions Section
        item {
            RecipeCarouselSection(
                title = stringResource(R.string.home_recipe_suggestions_title),
                subtitle = stringResource(R.string.home_recipe_suggestions_subtitle),
                recipes = uiState.forYouRecipes,
                onRecipeClick = onRecipeClick
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            RecipeCarouselSection(
                title = stringResource(R.string.home_recipe_suggestions_dietary_title),
                subtitle = stringResource(R.string.home_recipe_suggestions_dietary_subtitle),
                recipes = uiState.lowCarbRecipes,
                onRecipeClick = onRecipeClick
            )
        }

        item {
            RecipeCarouselSection(
                title = stringResource(R.string.home_recipe_suggestions_world_cuisine_title),
                subtitle = stringResource(R.string.home_recipe_suggestions_world_cuisine_subtitle),
                recipes = uiState.italianRecipes.shuffled(),
                onRecipeClick = onRecipeClick
            )
        }

        item {
            RecipeCarouselSection(
                title = stringResource(R.string.home_recipe_suggestions_dessets_title),
                subtitle = stringResource(R.string.home_recipe_suggestions_desserts_subtitle),
                recipes = uiState.dessertRecipes,
                onRecipeClick = onRecipeClick
            )
        }

    }
}

/**
 * A reusable section component for horizontally scrolling recipe carousels.
 *
 * @param title The section title displayed above the carousel
 * @param recipes List of recipe previews to display
 * @param onRecipeClick Callback when a recipe card is clicked
 */
@Composable
fun RecipeCarouselSection(
    title: String,
    subtitle: String,
    recipes: List<RecipePreview>,
    onRecipeClick: (UUID) -> Unit = {},
) {

    SectionHeaderWithSubtitle(title, subtitle)

    // Horizontal scrolling carousel
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(recipes) { recipe ->
            LargeCard(
                recipe = recipe,
                onClick = onRecipeClick
            )
        }
    }
    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_extra_small)))
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ChefAITheme {
        // Preview with placeholder snackbar state
        HomeScreen(
            snackbarHostState = SnackbarHostState()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    ChefAITheme(darkTheme = true) {
        // Preview with placeholder snackbar state
        HomeScreen(
            snackbarHostState = SnackbarHostState()
        )
    }
}