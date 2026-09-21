package com.tenmilelabs.chefai.recipes.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.preview.RecipePreviewProvider
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import java.util.UUID

/**
 * The Recipes tab's grid card (04): a 4:3 grayscale photo, title and meta line. Distinct from
 * [com.tenmilelabs.chefai.core.ui.components.RecipeListCard] — that one is the fixed-thumbnail row
 * used by search results and Home's "This Week" list; this is the two-column browse tile.
 */
@Composable
fun RecipeGridCard(
    recipe: RecipePreview,
    onClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("RecipeGridCard")
            .flatClickable(onClick = { onClick(recipe.uuid) }, role = Role.Button),
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
            colorFilter = grayscale,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = recipe.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = recipe.metaLine(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** "30m · Serves 4", dropping either half when the recipe does not carry it. */
private fun RecipePreview.metaLine(): String {
    val totalMinutes = prepTimeMinutes + cookTimeMinutes
    return listOfNotNull(
        totalMinutes.takeIf { it > 0 }?.let { "${it}m" },
        servings.takeIf { it > 0 }?.let { "Serves $it" },
    ).joinToString(" · ")
}

@Preview(name = "RecipeGridCard — Light")
@Composable
private fun RecipeGridCardPreview(
    @PreviewParameter(RecipePreviewProvider::class) recipe: RecipePreview,
) {
    ChefAITheme {
        Surface {
            RecipeGridCard(recipe = recipe, onClick = {}, modifier = Modifier.padding(16.dp))
        }
    }
}

@Preview(name = "RecipeGridCard — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecipeGridCardDarkPreview(
    @PreviewParameter(RecipePreviewProvider::class) recipe: RecipePreview,
) {
    ChefAITheme(darkTheme = true) {
        Surface {
            RecipeGridCard(recipe = recipe, onClick = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
