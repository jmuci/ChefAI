package com.tenmilelabs.chefai.collections.domain.model

import java.util.UUID

/**
 * A recipe the user has bookmarked, with the two marks a bookmark can carry.
 *
 * A recipe with no [RecipeBookmark] is simply not bookmarked — neither mark can exist without one.
 *
 * @property isStarred A higher tier of bookmark: a recipe the user really likes.
 * @property isGoTo A recipe the meal planner should favour — easy, cheap, fine to eat often.
 */
data class RecipeBookmark(
    val recipeId: UUID,
    val isStarred: Boolean = false,
    val isGoTo: Boolean = false,
)

/** A change to a recipe's bookmark, as the bookmark menu and the go-to toggle request it. */
sealed interface BookmarkUpdate {
    /** Bookmark the recipe. A no-op for its marks if it is already bookmarked. */
    data object Bookmark : BookmarkUpdate

    /** Star the recipe, bookmarking it first if it isn't. */
    data object Star : BookmarkUpdate

    /** Drop the star and keep the bookmark. */
    data object Unstar : BookmarkUpdate

    /** Remove the bookmark, and with it the star and the go-to mark. */
    data object Remove : BookmarkUpdate

    /** Mark or unmark the recipe as go-to. Ignored when the recipe isn't bookmarked. */
    data class SetGoTo(val goTo: Boolean) : BookmarkUpdate
}

/** Whether applying this update may create a bookmark row (and so needs the recipe locally). */
val BookmarkUpdate.createsBookmark: Boolean
    get() = this == BookmarkUpdate.Bookmark || this == BookmarkUpdate.Star
