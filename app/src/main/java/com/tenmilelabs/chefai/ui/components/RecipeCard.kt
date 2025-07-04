package com.tenmilelabs.chefai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.data.Recipe
import com.tenmilelabs.chefai.ui.theme.ChefAITheme

@Composable
fun RecipeCard(recipe: Recipe) {
    Card(
        modifier = Modifier
            .padding(dimensionResource(id = R.dimen.padding_small)),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .padding(dimensionResource(id = R.dimen.padding_small))
            ) {
                Image(
                    modifier = Modifier.fillMaxWidth(),
                    alignment = Alignment.Center,
                    contentScale = ContentScale.FillWidth,
                    colorFilter = ColorFilter.tint(Color.Gray),
                    painter = painterResource(id = R.drawable.ic_img_error),
                    contentDescription = stringResource(id = R.string.recipe_image_content_description)
                )
            }
            Column(
                modifier = Modifier
                    .weight(4f)
                    .padding(dimensionResource(id = R.dimen.padding_small))
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(horizontal = dimensionResource(id = R.dimen.padding_small))
                )
                Row(modifier = Modifier
                    .padding(vertical = dimensionResource(id = R.dimen.padding_extra_extra_small))) {
                    Column {
                        Text(
                            text = "${recipe.prepTime} min",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = dimensionResource(id = R.dimen.padding_small))
                        )
                    }
                    Column {
                        Surface(shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                text = recipe.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = dimensionResource(id = R.dimen.padding_small))
                            )
                        }
                    }
                }
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