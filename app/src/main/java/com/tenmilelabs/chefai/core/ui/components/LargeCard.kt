package com.tenmilelabs.chefai.core.ui.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import java.util.UUID

/**
 * Below this width there isn't room for the full "Tonight" hero layout (tags, meta row, a
 * full-width button) — [LargeCard] also renders the small square tile used in horizontal
 * carousels ([com.tenmilelabs.chefai.home.data.model.ComponentModel.SquaredCard]), which has no
 * dedicated design in this batch. That case degrades to image + title only, still flat, rather
 * than clipping the full stack.
 */
private val CompactWidthThreshold = 250.dp

/**
 * The "Tonight" hero card: a full-width photo, two tags, title, a time/servings meta
 * row, and a full-width "View Recipe" button — no card container, no gradient overlay. See
 * docs/design/modernist.md; the design has no bookmark affordance on this card.
 *
 * @param recipe The recipe preview data to display
 * @param modifier Modifier for customizing the card's size. Default size is 300x220dp.
 * @param isInCollection Unused while this card has no bookmark affordance (see above); kept so
 * this signature doesn't change out from under its callers.
 * @param onClick Callback when the card (or its button) is clicked, receives the recipe UUID
 * @param onSaveToCollection Unused for the same reason as [isInCollection].
 */
@Composable
fun LargeCard(
    recipe: RecipePreview,
    modifier: Modifier = Modifier
        .width(300.dp)
        .height(220.dp),
    isInCollection: Boolean = false,
    onClick: (UUID) -> Unit = {},
    onSaveToCollection: (UUID) -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier) {
        val isCompact = maxWidth < CompactWidthThreshold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = { onClick(recipe.uuid) }),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(recipeImageModel(recipe.localImagePath, recipe.imageUrlThumbnail))
                    .crossfade(true)
                    .build(),
                placeholder = painterResource(R.drawable.ic_img_placeholder),
                error = painterResource(R.drawable.ic_img_error),
                contentDescription = stringResource(R.string.recipe_image_content_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .let { if (isCompact) it.weight(1f) else it.aspectRatio(16f / 10f) },
            )

            if (isCompact) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                val chips = remember(recipe.tags, recipe.labels) {
                    listOfNotNull(
                        recipe.tags.firstOrNull()?.let { it.displayName to InfoChipType.TAG },
                        recipe.labels.firstOrNull()?.let { it.displayName to InfoChipType.LABEL },
                    )
                }
                if (chips.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        chips.forEach { (text, type) -> InfoChip(text = text, type = type) }
                    }
                }

                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    RecipeMeta(
                        icon = ChefAIIcons.Clock,
                        text = "${recipe.prepTimeMinutes + recipe.cookTimeMinutes}m",
                    )
                    RecipeMeta(
                        icon = ChefAIIcons.Users,
                        text = stringResource(R.string.recipe_servings_format, recipe.servings),
                    )
                }

                Button(
                    onClick = { onClick(recipe.uuid) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.view_recipe_button))
                }
            }
        }
    }
}

@Composable
private fun RecipeMeta(@DrawableRes icon: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(15.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "LargeCard — hero, Light")
@Composable
private fun LargeCardPreview() {
    ChefAITheme {
        Surface {
            LargeCard(recipe = PreviewData.grilledChickenRecipe)
        }
    }
}

@Preview(name = "LargeCard — hero, Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LargeCardDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface {
            LargeCard(recipe = PreviewData.grilledChickenRecipe)
        }
    }
}

@Preview(name = "LargeCard — square tile, Light")
@Composable
private fun LargeCardSquarePreview() {
    ChefAITheme {
        Surface {
            LargeCard(
                recipe = PreviewData.grilledChickenRecipe,
                modifier = Modifier.width(180.dp).height(180.dp),
            )
        }
    }
}

@Preview(name = "LargeCard — square tile, Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LargeCardSquareDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface {
            LargeCard(
                recipe = PreviewData.grilledChickenRecipe,
                modifier = Modifier.width(180.dp).height(180.dp),
            )
        }
    }
}
