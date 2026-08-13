package com.tenmilelabs.chefai.recipes.ui.urlimport.browser

import androidx.annotation.StringRes
import java.util.UUID

sealed interface BrowserImportAction {
    /** A snapshot of the rendered DOM, pushed up from the WebView's polling loop. */
    data class SnapshotCaptured(val html: String) : BrowserImportAction

    /** The page has been up long enough that the user is probably being asked to do something. */
    data object SettleTimeoutElapsed : BrowserImportAction

    /** Nothing readable ever appeared, after a generously long wait. */
    data object GiveUp : BrowserImportAction

    data object EnterManually : BrowserImportAction
    data object Cancel : BrowserImportAction
}

data class BrowserImportState(
    val url: String = "",
    val phase: Phase = Phase.LOADING,
    /**
     * A snapshot is being parsed and mapped right now — a concurrency guard only, never surfaced
     * in the UI. It's true for a few milliseconds on every failed poll (the common case, while the
     * check sits unsolved), so displaying it would flicker the banner in and out several times a
     * second rather than communicate anything real.
     */
    val isExtracting: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
) {
    /** True while the WebView should keep being polled for a recipe. */
    val isPolling: Boolean = errorRes == null && !isExtracting

    enum class Phase {
        /** The page is loading and nothing has asked the user for anything yet. */
        LOADING,

        /**
         * Long enough has passed that the page is probably showing a bot check. Detecting one
         * outright would mean pattern-matching every vendor's markup and re-doing it whenever they
         * change it; a timer says the same thing to the user and never goes stale.
         */
        AWAITING_USER,
    }
}

sealed interface BrowserImportEffect {
    data class NavigateToEditorWithDraft(val draftId: UUID) : BrowserImportEffect
    data object NavigateToManualEditor : BrowserImportEffect
    data object NavigateBack : BrowserImportEffect
}
