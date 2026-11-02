package com.tenmilelabs.chefai.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.collections.domain.model.BookmarkUpdate
import com.tenmilelabs.chefai.collections.domain.model.RecipeBookmark
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.preview.RecipePreviewProvider
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import java.util.UUID

/** 76×60 — the fixed thumbnail size in the design's search-result and recipe-list row. */
private val ThumbnailWidth = 76.dp
private val ThumbnailHeight = 60.dp

/**
 * @param modifier Merged with this row's own padding/dividers so a caller's modifier is
 * never silently dropped.
 * @param bookmark [recipe]'s bookmark, or `null` when it isn't bookmarked — drives the bookmark
 * glyph and, once bookmarked, the go-to toggle beside it. Only read when [onBookmarkUpdate] is
 * non-null.
 * @param onBookmarkUpdate When non-null, shows the [BookmarkMenu] button (and, for a bookmarked
 * recipe, the [GoToToggleButton]); every change is handed here. Left null (the default) where a
 * row doesn't want inline saving — introduced for search results, where saving without opening the
 * recipe matters.
 * @param showDivider Whether to draw the 1dp row rule under the row. On by default for the ruled
 * lists; off inside a panel (Home, Search results), where rows separate by spacing instead — and
 * where the press tint sinks to `chefColors.panelGround` rather than lifting to neutral-100.
 */
@Composable
fun RecipeListCard(
    recipe: RecipePreview,
    modifier: Modifier = Modifier,
    bookmark: RecipeBookmark? = null,
    onBookmarkUpdate: ((BookmarkUpdate) -> Unit)? = null,
    showDivider: Boolean = true,
    navigateToDetail: (UUID) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // neutral-100 lifts a row off the plain ground, but it *is* the panel surface in light — on a
    // panel the press has to sink to the page tone instead to show at all.
    val pressedFill = if (showDivider) {
        MaterialTheme.chefColors.neutral.s100
    } else {
        MaterialTheme.chefColors.panelGround
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ThumbnailHeight)
                .background(if (pressed) pressedFill else Color.Unspecified)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { navigateToDetail(recipe.uuid) },
                )
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                    .size(width = ThumbnailWidth, height = ThumbnailHeight)
                    .clip(RoundedCornerShape(MaterialTheme.chefColors.mediaRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = recipe.metaLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            if (onBookmarkUpdate != null) {
                if (bookmark != null) {
                    GoToToggleButton(
                        isGoTo = bookmark.isGoTo,
                        onToggle = { onBookmarkUpdate(BookmarkUpdate.SetGoTo(!bookmark.isGoTo)) },
                        size = 32.dp,
                    )
                }
                BookmarkMenu(bookmark = bookmark, onUpdate = onBookmarkUpdate)
            }
        }

        if (showDivider) {
            HorizontalDivider(
                thickness = MaterialTheme.chefColors.rowRuleWidth,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** "10m · Serves 1", dropping either half when the recipe does not carry it. */
private fun RecipePreview.metaLine(): String {
    val totalMinutes = prepTimeMinutes + cookTimeMinutes
    return listOfNotNull(
        totalMinutes.takeIf { it > 0 }?.let { "${it}m" },
        servings.takeIf { it > 0 }?.let { "Serves $it" },
    ).joinToString(" · ")
}

@Preview(name = "RecipeListCard — Light")
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

@Preview(name = "RecipeListCard — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RecipeListCardDarkPreview(
    @PreviewParameter(RecipePreviewProvider::class) recipe: RecipePreview
) {
    ChefAITheme(darkTheme = true) {
        Surface {
            RecipeListCard(recipe = recipe)
        }
    }
}

@Preview(name = "RecipeListCard — bookmarkable, Light")
@Composable
private fun RecipeListCardBookmarkablePreview(
    @PreviewParameter(RecipePreviewProvider::class) recipe: RecipePreview
) {
    ChefAITheme {
        Surface {
            RecipeListCard(recipe = recipe, bookmark = RecipeBookmark(recipe.uuid), onBookmarkUpdate = {})
        }
    }
}

@Preview(name = "RecipeListCard — starred go-to, Light")
@Composable
private fun RecipeListCardStarredGoToPreview(
    @PreviewParameter(RecipePreviewProvider::class) recipe: RecipePreview
) {
    ChefAITheme {
        Surface {
            RecipeListCard(
                recipe = recipe,
                bookmark = RecipeBookmark(recipe.uuid, isStarred = true, isGoTo = true),
                onBookmarkUpdate = {},
            )
        }
    }
}

@Preview(name = "RecipeListCard — starred go-to, Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecipeListCardStarredGoToDarkPreview(
    @PreviewParameter(RecipePreviewProvider::class) recipe: RecipePreview
) {
    ChefAITheme(darkTheme = true) {
        Surface {
            RecipeListCard(
                recipe = recipe,
                bookmark = RecipeBookmark(recipe.uuid, isStarred = true, isGoTo = true),
                onBookmarkUpdate = {},
            )
        }
    }
}
