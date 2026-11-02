package com.tenmilelabs.chefai.home.ui

import com.tenmilelabs.chefai.collections.domain.model.BookmarkUpdate
import java.util.UUID

sealed interface HomeAction {
    data class CardClicked(val recipeId: String) : HomeAction
    data class SectionActionClicked(val actionUrl: String) : HomeAction
    /** A bookmark-menu choice or go-to toggle on a recipe card — see [BookmarkUpdate]. */
    data class BookmarkUpdated(val recipeId: UUID, val update: BookmarkUpdate) : HomeAction
}
