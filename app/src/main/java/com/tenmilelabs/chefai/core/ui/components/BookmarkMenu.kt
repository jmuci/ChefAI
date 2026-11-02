package com.tenmilelabs.chefai.core.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.collections.domain.model.BookmarkUpdate
import com.tenmilelabs.chefai.collections.domain.model.RecipeBookmark
import com.tenmilelabs.chefai.core.ui.components.flat.FlatMenuPanel
import com.tenmilelabs.chefai.core.ui.components.flat.FlatMenuPopup
import com.tenmilelabs.chefai.core.ui.components.flat.FlatMenuRow
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import java.util.UUID

/** The bookmark glyph for [bookmark]'s state: outline, filled, or a filled star once starred. */
@DrawableRes
fun bookmarkIconRes(bookmark: RecipeBookmark?): Int = when {
    bookmark == null -> ChefAIIcons.Bookmark
    bookmark.isStarred -> ChefAIIcons.StarFilled
    else -> ChefAIIcons.BookmarkFilled
}

/**
 * Names the state, not just the action: the glyph is the only other signal, and a screen reader
 * cannot see it.
 */
@StringRes
fun bookmarkContentDescriptionRes(bookmark: RecipeBookmark?): Int = when {
    bookmark == null -> R.string.save_to_collection_content_description
    bookmark.isStarred -> R.string.starred_content_description
    else -> R.string.bookmarked_content_description
}

/**
 * A recipe's bookmark button and the menu it opens.
 *
 * Not bookmarked, the menu offers **Bookmark** and **Star** (a star bookmarks too). Bookmarked, it
 * offers **Star** (or **Remove star**) and **Remove bookmark**. Each row hands [onUpdate] the matching
 * [BookmarkUpdate]; the menu holds no state beyond whether it is open.
 *
 * @param bookmark The recipe's bookmark, or `null` when it isn't bookmarked.
 * @param anchor The button that opens the menu. Defaults to a plain [BookmarkIconButton]; the
 *   recipe details hero passes its own photo-overlay button instead.
 */
@Composable
fun BookmarkMenu(
    bookmark: RecipeBookmark?,
    onUpdate: (BookmarkUpdate) -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable (onClick: () -> Unit) -> Unit = { onClick ->
        BookmarkIconButton(bookmark = bookmark, onClick = onClick)
    },
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        anchor { expanded = true }
        if (expanded) {
            FlatMenuPopup(onDismissRequest = { expanded = false }) {
                BookmarkMenuRows(
                    bookmark = bookmark,
                    onUpdate = { update ->
                        expanded = false
                        onUpdate(update)
                    },
                )
            }
        }
    }
}

/** The bookmark glyph as an 18dp icon on the ground, accent-tinted once saved. */
@Composable
fun BookmarkIconButton(
    bookmark: RecipeBookmark?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTag("SaveToCollectionButton"),
    ) {
        Icon(
            painter = painterResource(bookmarkIconRes(bookmark)),
            contentDescription = stringResource(bookmarkContentDescriptionRes(bookmark)),
            // Accent as an icon directly on the ground needs the AA-safe step, not the brand
            // fill — see ChefColors.accentText.
            tint = bookmarkTint(bookmark, idle = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Accent once the recipe is saved; [idle] otherwise. */
@Composable
fun bookmarkTint(bookmark: RecipeBookmark?, idle: Color): Color =
    if (bookmark != null) MaterialTheme.chefColors.accentText else idle

@Composable
private fun BookmarkMenuRows(
    bookmark: RecipeBookmark?,
    onUpdate: (BookmarkUpdate) -> Unit,
) {
    if (bookmark == null) {
        FlatMenuRow(
            label = stringResource(R.string.bookmark_menu_bookmark),
            icon = ChefAIIcons.Bookmark,
            onClick = { onUpdate(BookmarkUpdate.Bookmark) },
            modifier = Modifier.testTag("BookmarkMenuBookmark"),
        )
        RowRule()
        FlatMenuRow(
            label = stringResource(R.string.bookmark_menu_star),
            icon = ChefAIIcons.Star,
            onClick = { onUpdate(BookmarkUpdate.Star) },
            modifier = Modifier.testTag("BookmarkMenuStar"),
        )
    } else {
        if (bookmark.isStarred) {
            FlatMenuRow(
                label = stringResource(R.string.bookmark_menu_unstar),
                icon = ChefAIIcons.StarFilled,
                onClick = { onUpdate(BookmarkUpdate.Unstar) },
                modifier = Modifier.testTag("BookmarkMenuUnstar"),
            )
        } else {
            FlatMenuRow(
                label = stringResource(R.string.bookmark_menu_star),
                icon = ChefAIIcons.Star,
                onClick = { onUpdate(BookmarkUpdate.Star) },
                modifier = Modifier.testTag("BookmarkMenuStar"),
            )
        }
        RowRule()
        FlatMenuRow(
            label = stringResource(R.string.bookmark_menu_remove),
            icon = ChefAIIcons.Trash,
            // The deep accent step carries destructive intent in this mono palette, as the
            // profile menu's Logout does.
            contentColor = MaterialTheme.chefColors.accentText,
            onClick = { onUpdate(BookmarkUpdate.Remove) },
            modifier = Modifier.testTag("BookmarkMenuRemove"),
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────────────────────
// The panel is previewed directly; a Popup does not render in the preview pane.

private val PreviewRecipeId = UUID.fromString("00000000-0000-0000-0000-000000000001")

@Preview(name = "Bookmark menu — not bookmarked", showBackground = true)
@Preview(name = "Bookmark menu — not bookmarked, dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BookmarkMenuNotBookmarkedPreview() {
    ChefAITheme { FlatMenuPanel { BookmarkMenuRows(bookmark = null, onUpdate = {}) } }
}

@Preview(name = "Bookmark menu — bookmarked", showBackground = true)
@Preview(name = "Bookmark menu — bookmarked, dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BookmarkMenuBookmarkedPreview() {
    ChefAITheme { FlatMenuPanel { BookmarkMenuRows(bookmark = RecipeBookmark(PreviewRecipeId), onUpdate = {}) } }
}

@Preview(name = "Bookmark menu — starred", showBackground = true)
@Preview(name = "Bookmark menu — starred, dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BookmarkMenuStarredPreview() {
    ChefAITheme {
        FlatMenuPanel {
            BookmarkMenuRows(bookmark = RecipeBookmark(PreviewRecipeId, isStarred = true), onUpdate = {})
        }
    }
}
