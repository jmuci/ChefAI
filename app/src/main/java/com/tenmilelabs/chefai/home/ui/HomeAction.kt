package com.tenmilelabs.chefai.home.ui

import java.util.UUID

sealed interface HomeAction {
    data class CardClicked(val recipeId: String) : HomeAction
    data class SectionActionClicked(val actionUrl: String) : HomeAction
    data class BookmarkToggled(val recipeId: UUID) : HomeAction
}
