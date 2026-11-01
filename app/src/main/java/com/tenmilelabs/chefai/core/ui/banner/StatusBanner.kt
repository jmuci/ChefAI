package com.tenmilelabs.chefai.core.ui.banner

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin Compose-facing adapter over the app-wide [StatusBannerController] — same pattern as
 * `RecipeTimerViewModel`: the state lives in the singleton, so whichever `ViewModelStoreOwner`
 * this is scoped to sees the same banner.
 */
@HiltViewModel
class StatusBannerViewModel @Inject constructor(
    private val controller: StatusBannerController,
) : ViewModel() {
    val message: StateFlow<StatusBannerMessage?> = controller.message

    fun dismiss(id: Long) = controller.dismiss(id)
}

/**
 * The app-wide status banner: a full-width strip that hides itself after
 * [StatusBannerMessage.displayDuration], or on tap of its close button. It draws only the
 * messages whose [StatusBannerMessage.placement] matches [placement]:
 *
 * - [StatusBannerPlacement.BOTTOM] — host it directly above the bottom nav. It grows upward from
 *   the nav's top edge, so it reads as sliding out from under it. Where no nav is drawn below it
 *   (login, wizards, the expanded-width rail layout), pass [insetForNavigationBar] so it clears the
 *   system nav bar itself.
 * - [StatusBannerPlacement.TOP] — host it as an overlay pinned to the top of the screen. It drops
 *   down over the content and fills in behind the status bar.
 */
@Composable
fun StatusBanner(
    placement: StatusBannerPlacement,
    modifier: Modifier = Modifier,
    insetForNavigationBar: Boolean = false,
    viewModel: StatusBannerViewModel = hiltViewModel(),
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    StatusBannerContent(
        allMessages = message,
        placement = placement,
        insetForNavigationBar = insetForNavigationBar,
        onDismiss = viewModel::dismiss,
        modifier = modifier,
    )
}

/** Stateless content, hoisted out of [StatusBanner] so it's previewable without Hilt. */
@Composable
internal fun StatusBannerContent(
    allMessages: StatusBannerMessage?,
    placement: StatusBannerPlacement,
    insetForNavigationBar: Boolean,
    onDismiss: (id: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // The other placement's host owns that message — including its countdown.
    val message = allMessages?.takeIf { it.placement == placement }

    // Keyed on the id, not the message, so re-showing the same text restarts the countdown.
    LaunchedEffect(message?.id) {
        val shown = message ?: return@LaunchedEffect
        delay(shown.displayDuration)
        currentOnDismiss(shown.id)
    }

    // AnimatedVisibility keeps rendering its content through the exit animation, after `message`
    // has already gone null — so hold on to the last one shown.
    val lastShown = remember { LastShown() }
    if (message != null) lastShown.value = message

    AnimatedVisibility(
        visible = message != null,
        modifier = modifier.fillMaxWidth(),
        enter = when (placement) {
            // Slides down from above the screen's top edge, over the content.
            StatusBannerPlacement.TOP -> slideInVertically { fullHeight -> -fullHeight }
            // Grow from the top edge with the content pinned to it: the banner's top appears at
            // the nav's seam and rises, its lower part still "behind" the nav until fully revealed.
            StatusBannerPlacement.BOTTOM -> expandVertically(expandFrom = Alignment.Top)
        },
        exit = when (placement) {
            StatusBannerPlacement.TOP -> slideOutVertically { fullHeight -> -fullHeight }
            StatusBannerPlacement.BOTTOM -> shrinkVertically(shrinkTowards = Alignment.Top)
        },
    ) {
        lastShown.value?.let { shown ->
            StatusBannerStrip(
                message = shown,
                insets = when {
                    placement == StatusBannerPlacement.TOP -> WindowInsets.statusBars
                    insetForNavigationBar -> WindowInsets.navigationBars
                    else -> null
                },
                onDismiss = { currentOnDismiss(shown.id) },
            )
        }
    }
}

private class LastShown(var value: StatusBannerMessage? = null)

@Composable
private fun StatusBannerStrip(
    message: StatusBannerMessage,
    /** System bars the strip's fill extends behind, padding its content clear of them. */
    insets: WindowInsets?,
    onDismiss: () -> Unit,
) {
    val (container, content) = message.tone.colors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(container)
            .then(if (insets != null) Modifier.windowInsetsPadding(insets) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(message.message),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Icon(
                painter = painterResource(ChefAIIcons.X),
                contentDescription = stringResource(R.string.status_banner_dismiss),
                tint = content,
                modifier = Modifier
                    .flatClickable(
                        onClick = onDismiss,
                        role = Role.Button,
                        pressedTint = content.copy(alpha = 0.12f),
                    )
                    .padding(14.dp)
                    .size(16.dp),
            )
        }
    }
}

/** The fill and the text/icon color on it, for this tone. */
@Composable
private fun StatusBannerTone.colors(): Pair<Color, Color> = when (this) {
    StatusBannerTone.PRIMARY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    StatusBannerTone.WARNING -> MaterialTheme.chefColors.bannerWarning to MaterialTheme.chefColors.onBannerWarning
    StatusBannerTone.ERROR -> MaterialTheme.chefColors.bannerError to MaterialTheme.chefColors.onBannerError
}

@Preview(name = "Status banner — light")
@Preview(name = "Status banner — dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun StatusBannerPreview() {
    ChefAITheme {
        Column {
            StatusBannerTone.entries.forEachIndexed { index, tone ->
                StatusBannerContent(
                    allMessages = StatusBannerMessage(
                        message = when (tone) {
                            StatusBannerTone.WARNING -> R.string.status_banner_offline
                            else -> R.string.status_banner_server_error
                        },
                        tone = tone,
                        id = index.toLong(),
                    ),
                    placement = StatusBannerPlacement.BOTTOM,
                    insetForNavigationBar = false,
                    onDismiss = {},
                )
            }
        }
    }
}
