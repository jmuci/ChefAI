package com.tenmilelabs.chefai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.domain.model.RecipePreview
import com.tenmilelabs.chefai.ui.preview.RecipePreviewProvider
import com.tenmilelabs.chefai.ui.theme.ChefAITheme
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeListCard(
    recipe: RecipePreview,
    navigateToDetail: (UUID) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .padding(horizontal = dimensionResource(id = R.dimen.padding_small))
            .height(dimensionResource(id = R.dimen.recipe_card_height))
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(
            defaultElevation = dimensionResource(id = R.dimen.padding_extra_small)
        ),
        onClick = { navigateToDetail(recipe.uuid) }
    ) {
        Row(
            modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_small)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(recipe.imageUrlThumbnail)
                    .crossfade(true)
                    .build(),
                placeholder = painterResource(R.drawable.ic_img_placeholder),
                error = painterResource(R.drawable.ic_img_error),
                contentDescription = stringResource(R.string.recipe_image_content_description),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 100.dp, height = 100.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column(
                modifier = Modifier
                    .padding(start = dimensionResource(id = R.dimen.padding_medium))
                    .weight(1f)
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                RecipeTimeRow(
                    prepTime = recipe.prepTimeMinutes,
                    cookTime = recipe.cookTimeMinutes
                )

                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_small)))

                Text(
                    text = recipe.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_extra_small)))

                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_small)))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_extra_small)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_extra_small)),
                    maxLines = 1
                ) {
                    recipe.labels.take(3).forEach { label ->
                        InfoChip(text = label.displayName, type = InfoChipType.LABEL)
                    }
                }
            }
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
            RecipeListCard(recipe = recipe)
        }
    }
}
