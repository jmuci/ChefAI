package com.tenmilelabs.chefai.recipes.ui.import

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
