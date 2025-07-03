package com.tenmilelabs.chefai.ui.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.data.Recipe
import com.tenmilelabs.chefai.ui.components.RecipeCard
import com.tenmilelabs.chefai.ui.theme.ChefAITheme

@Composable
fun RecipesScreen(
    recipes: List<Recipe> = emptyList()
) {
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
                RecipeCard(recipe = recipe)
            }
        }
    }
}


@Preview
@Composable
fun RecipesScreenPreview() {
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
            RecipesScreen(recipes)
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