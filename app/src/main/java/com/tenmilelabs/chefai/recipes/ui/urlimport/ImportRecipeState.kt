package com.tenmilelabs.chefai.recipes.ui.urlimport

import androidx.annotation.StringRes
import java.util.UUID

sealed interface ImportAction {
    data class UrlChanged(val url: String) : ImportAction
    data object Import : ImportAction
    data object ClearError : ImportAction
    data object EnterManually : ImportAction
}

sealed interface ImportEffect {
    data class NavigateToEditorWithDraft(val draftId: UUID) : ImportEffect

    /** Opens the in-app browser so the user can clear the site's bot check. */
    data class NavigateToBrowserImport(val url: String) : ImportEffect

    data object NavigateToManualEditor : ImportEffect
    data object NavigateBack : ImportEffect
}

data class ImportRecipeState(
    val url: String = "",
    val isImporting: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    val showManualEntryOption: Boolean = false,
) {
    val canImport: Boolean = url.isNotBlank() && !isImporting
}
