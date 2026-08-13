package com.tenmilelabs.chefai.recipes.ui.urlimport.browser

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.recipes.data.network.ScraperWebViewClient
import com.tenmilelabs.chefai.recipes.data.network.applyScraperHardening
import com.tenmilelabs.chefai.recipes.data.network.readRenderedHtml
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Matches the off-screen fetcher's cadence — cheap, and fast enough to feel instant. */
private val SNAPSHOT_INTERVAL = 500.milliseconds

/** After this, the hint switches from "loading" to "you may need to do something". */
private val SETTLE_TIMEOUT = 3.seconds

/** How long the user gets before the screen admits defeat and offers manual entry. */
private val GIVE_UP_TIMEOUT = 60.seconds

@Composable
fun BrowserImportRoute(
    onNavigateToEditorWithDraft: (UUID) -> Unit,
    onNavigateToManualEditor: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: BrowserImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BrowserImportEffect.NavigateToEditorWithDraft -> onNavigateToEditorWithDraft(effect.draftId)
                BrowserImportEffect.NavigateToManualEditor -> onNavigateToManualEditor()
                BrowserImportEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    BrowserImportScreen(state = state, onAction = viewModel::dispatch)
}

/**
 * The last resort for a site that refuses automated fetches: show it to the user in a real browser.
 *
 * Nothing here tries to defeat the bot check — the user clears it themselves, exactly as they would
 * in Chrome. The rendered DOM is polled throughout, so the moment the real page appears the recipe
 * is picked up and the screen gets out of the way.
 */
@Composable
fun BrowserImportScreen(
    state: BrowserImportState,
    onAction: (BrowserImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag("BrowserImportScreen").fillMaxSize()) {
        BrowserImportBanner(state = state, onAction = onAction)

        if (state.errorRes == null) {
            RecipePageWebView(
                url = state.url,
                isPolling = state.isPolling,
                onAction = onAction,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BrowserImportBanner(
    state: BrowserImportState,
    onAction: (BrowserImportAction) -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.padding_medium))) {
            // Deliberately not keyed on state.isExtracting: a parse attempt on a snapshot with no
            // recipe on it (the common case, every 500ms while the check sits unsolved) resolves
            // in milliseconds, and briefly swapping the hint text in and out reflows this banner
            // between one and two lines several times a second — the "page keeps reloading" look.
            // A real recipe extraction is only ever visible for that same instant before the whole
            // screen navigates away, so there is nothing worth surfacing here either way.
            val hintRes = when {
                state.errorRes != null -> state.errorRes
                state.phase == BrowserImportState.Phase.AWAITING_USER -> R.string.import_recipe_browser_hint
                else -> R.string.import_recipe_browser_loading
            }
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.errorRes != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            if (state.errorRes == null) {
                Spacer(Modifier.height(dimensionResource(R.dimen.padding_small)))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))) {
                TextButton(onClick = { onAction(BrowserImportAction.Cancel) }) {
                    Text(stringResource(R.string.import_recipe_browser_cancel))
                }
                if (state.errorRes != null) {
                    TextButton(onClick = { onAction(BrowserImportAction.EnterManually) }) {
                        Text(stringResource(R.string.import_recipe_enter_manually))
                    }
                }
            }
        }
    }
}

/**
 * Hosts the WebView and drives the polling loop.
 *
 * The View is created once and kept across recompositions — recreating it would restart the load
 * and throw away a bot check the user had already cleared.
 */
@Composable
private fun RecipePageWebView(
    url: String,
    isPolling: Boolean,
    onAction: (BrowserImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            applyScraperHardening()
            webViewClient = ScraperWebViewClient()
        }
    }

    LaunchedEffect(webView, url) {
        if (url.isNotBlank()) webView.loadUrl(url)
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
            // Commits any clearance cookie the user just earned to disk, so the off-screen fetcher
            // can pick it up on the next import instead of showing this screen again.
            CookieManager.getInstance().flush()
        }
    }

    val currentIsPolling by rememberUpdatedState(isPolling)
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(webView) {
        var elapsed = 0.seconds
        var settled = false
        while (elapsed < GIVE_UP_TIMEOUT) {
            delay(SNAPSHOT_INTERVAL)
            elapsed += SNAPSHOT_INTERVAL
            if (!settled && elapsed >= SETTLE_TIMEOUT) {
                settled = true
                currentOnAction(BrowserImportAction.SettleTimeoutElapsed)
            }
            if (currentIsPolling) {
                webView.readRenderedHtml { html ->
                    if (html != null) currentOnAction(BrowserImportAction.SnapshotCaptured(html))
                }
            }
        }
        currentOnAction(BrowserImportAction.GiveUp)
    }

    AndroidView(factory = { webView }, modifier = modifier.testTag("BrowserImportWebView"))
}

@Preview(name = "Loading — Light", showBackground = true)
@Composable
private fun BrowserImportLoadingLightPreview() {
    ChefAITheme(darkTheme = false) {
        BrowserImportScreen(
            state = BrowserImportState(url = "https://example.com/recipe"),
            onAction = {},
        )
    }
}

@Preview(name = "Awaiting user — Dark", showBackground = true)
@Composable
private fun BrowserImportAwaitingUserDarkPreview() {
    ChefAITheme(darkTheme = true) {
        BrowserImportScreen(
            state = BrowserImportState(
                url = "https://example.com/recipe",
                phase = BrowserImportState.Phase.AWAITING_USER,
            ),
            onAction = {},
        )
    }
}

@Preview(name = "Gave up — Light", showBackground = true)
@Composable
private fun BrowserImportGaveUpLightPreview() {
    ChefAITheme(darkTheme = false) {
        BrowserImportScreen(
            state = BrowserImportState(
                url = "https://example.com/recipe",
                errorRes = R.string.import_recipe_browser_gave_up,
            ),
            onAction = {},
        )
    }
}
