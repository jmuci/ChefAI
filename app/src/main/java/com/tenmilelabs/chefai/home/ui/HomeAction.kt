package com.tenmilelabs.chefai.home.ui

sealed interface HomeAction {
    data class CardClicked(val recipeId: String) : HomeAction
    data class SectionActionClicked(val actionUrl: String) : HomeAction
}
