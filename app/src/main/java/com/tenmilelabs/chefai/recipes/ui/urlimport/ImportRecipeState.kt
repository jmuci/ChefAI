package com.tenmilelabs.chefai.recipes.ui.urlimport

import androidx.annotation.StringRes
import java.net.URI
import java.util.UUID

sealed interface ImportAction {
    data class UrlChanged(val url: String) : ImportAction
    data object Import : ImportAction
    data object ClearError : ImportAction
    data object EnterManually : ImportAction

    /** The user agreed to open [ImportRecipeState.browserConfirmUrl] in the in-app browser. */
    data object ConfirmOpenBrowser : ImportAction

    /** The user declined; the URL stays in the field and Import still works. */
    data object DismissOpenBrowser : ImportAction
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
    /**
     * Set when a URL shared in from another app needs the visible in-app browser. Another app
     * chose that URL, so the user confirms before a third party's page opens under Pocket Chef's
     * own top bar (security review A6).
     */
    val browserConfirmUrl: String? = null,
) {
    val canImport: Boolean = url.isNotBlank() && !isImporting

    /** The host named in the confirmation, or the whole URL if it has none. */
    val browserConfirmHost: String? =
        browserConfirmUrl?.let { runCatching { URI(it).host }.getOrNull() ?: it }
}
