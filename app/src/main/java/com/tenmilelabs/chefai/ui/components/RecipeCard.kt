package com.tenmilelabs.chefai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.data.Recipe
import com.tenmilelabs.chefai.ui.theme.ChefAITheme

@Composable
fun RecipeCard(
    recipe: Recipe,
    closeDetailScreen: () -> Unit = {},
    navigateToDetail: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .padding(dimensionResource(id = R.dimen.padding_small)),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
        onClick = { navigateToDetail(recipe.uuid) }
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .padding(dimensionResource(id = R.dimen.padding_small))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(recipe.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    placeholder = painterResource(R.drawable.ic_img_placeholder),
                    error = painterResource(R.drawable.ic_img_error),
                    contentDescription = stringResource(R.string.recipe_image_content_description),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp, 70.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
            }
            Column(
                modifier = Modifier
                    .weight(3f)
                    .padding(dimensionResource(id = R.dimen.padding_extra_small))
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(horizontal = dimensionResource(id = R.dimen.padding_small))
                )
                RecipeTimeAndLabelRow(recipe.prepTime, recipe.label)

                Text(
                    text = recipe.description,
                    style = MaterialTheme.typography.bodyMedium,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    modifier = Modifier
                        .padding(horizontal = dimensionResource(id = R.dimen.padding_small))
                )
            }
        }

    }
}

@Composable
fun RecipeTimeAndLabelRow(prepTime: Int, label: String) {
    Row(
        modifier = Modifier
            .padding(vertical = dimensionResource(id = R.dimen.padding_extra_extra_small))
    ) {
        Column {
            Text(
                text = "${prepTime} min",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = dimensionResource(id = R.dimen.padding_small))
            )
        }
        Column {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = dimensionResource(id = R.dimen.padding_small))
                )
            }
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
@Preview
@Composable
fun RecipeCardPreview() {
    ChefAITheme {
        Surface {
            RecipeCard(
                recipe = Recipe(
                    title = "Recipe Title",
                    label = "Recipe Label",
                    description = "Recipe Description. This is how you do this. Follow exactly the following steps to achieve success. \n No cutting corners.",
                    prepTime = 10,
                    recipeUrl = "https://www.google.com",
                    imageUrl = "https://www.google.com",
                    thumbnailUrl = "https://www.google.com",
                )
            )
        }
    }
}