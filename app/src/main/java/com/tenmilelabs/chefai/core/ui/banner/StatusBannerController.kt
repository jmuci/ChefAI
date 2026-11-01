package com.tenmilelabs.chefai.core.ui.banner

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** What a [StatusBanner] is reporting — picks its fill. */
enum class StatusBannerTone {
    /** Neutral news, in the brand color (`colorScheme.primary`). */
    PRIMARY,

    /** Something is degraded but the user can act on it (e.g. offline) — yellow. */
    WARNING,

    /** Something failed — red. */
    ERROR,
}

/** Where a [StatusBanner] appears. */
enum class StatusBannerPlacement {
    /** Drops down from the top edge of the screen, over the content. */
    TOP,

    /** Surfaces from behind the bottom nav (or from the bottom edge where there is none). */
    BOTTOM,
}

/**
 * One message for the app-wide [StatusBanner].
 *
 * [id] tells two otherwise-equal messages apart, so showing the same error twice restarts its
 * auto-hide countdown instead of being swallowed by `StateFlow`'s equality conflation.
 */
data class StatusBannerMessage(
    @param:StringRes val message: Int,
    val tone: StatusBannerTone,
    val displayDuration: Duration = DefaultDisplayDuration,
    val placement: StatusBannerPlacement = StatusBannerPlacement.BOTTOM,
    val id: Long,
) {
    companion object {
        val DefaultDisplayDuration: Duration = 5.minutes
    }
}

/**
 * App-wide holder for the single [StatusBanner] message. Any ViewModel injects it and calls [show];
 * `ChefAINavGraph` hosts one banner per [StatusBannerPlacement] — above the bottom nav, and over
 * the top of the screen — and each draws only the messages placed there.
 *
 * Deliberately owns no coroutine scope: the auto-hide countdown runs in the banner's own
 * composition (keyed on [StatusBannerMessage.id]) and calls [dismiss] when it elapses. A newer
 * message replaces the current one.
 */
@Singleton
class StatusBannerController @Inject constructor() {

    private val _message = MutableStateFlow<StatusBannerMessage?>(null)
    val message: StateFlow<StatusBannerMessage?> = _message.asStateFlow()

    private var nextId = 0L

    /** Shows [message] in the given [tone], replacing whatever banner is currently up. */
    fun show(
        @StringRes message: Int,
        tone: StatusBannerTone,
        displayDuration: Duration = StatusBannerMessage.DefaultDisplayDuration,
        placement: StatusBannerPlacement = StatusBannerPlacement.BOTTOM,
    ) {
        _message.value = StatusBannerMessage(message, tone, displayDuration, placement, id = nextId++)
    }

    /**
     * Hides the banner — but only if it is still showing message [id], so a countdown that elapses
     * just after a newer message replaced it can't take the newer one down with it.
     */
    fun dismiss(id: Long) {
        _message.update { current -> if (current?.id == id) null else current }
    }
}
