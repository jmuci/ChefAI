package com.tenmilelabs.chefai.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.LargeCard
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import java.util.UUID

/**
 * Home screen with horizontally scrolling recipe carousels.
 * Uses LazyColumn for efficient scrolling of multiple horizontal rows.
 */
@Composable
fun HomeScreen(
    onRecipeClick: (UUID) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Recipe Suggestions Section
        item {
            RecipeCarouselSection(
                title = stringResource(R.string.home_recipe_suggestions_title),
                subtilte = stringResource(R.string.home_recipe_suggestions_subtitle),
                recipes = getSampleRecipes(), // Using static test data
                onRecipeClick = onRecipeClick
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            RecipeCarouselSection(
                title = stringResource(R.string.home_recipe_suggestions_dietary_title),
                subtilte = stringResource(R.string.home_recipe_suggestions_dietary_subtitle),
                recipes = getSampleRecipes().shuffled(), // Using static test data
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
    subtilte: String,
    recipes: List<RecipePreview>,
    onRecipeClick: (UUID) -> Unit = {},
) {
    // Section title
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    Text(
        text = subtilte,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = dimensionResource(id = R.dimen.padding_extra_small))
    )

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
}

/**
 * Returns sample recipe data for preview and testing.
 * This will be replaced with actual repository data once connected.
 */
private fun getSampleRecipes(): List<RecipePreview> {
    return PreviewData.recipePreviewList
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ChefAITheme {
        HomeScreen()
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    ChefAITheme(darkTheme = true) {
        HomeScreen()
    }
}
