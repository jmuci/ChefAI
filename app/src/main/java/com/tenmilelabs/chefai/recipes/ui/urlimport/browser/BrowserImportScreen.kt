package com.tenmilelabs.chefai.recipes.ui.urlimport.browser

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.navigation.ChefAINavigation
import com.tenmilelabs.chefai.core.ui.navigation.ChefAITopAppBar
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.recipes.data.network.ScraperWebViewClient
import com.tenmilelabs.chefai.recipes.data.network.applyScraperHardening
import com.tenmilelabs.chefai.recipes.data.network.readRenderedHtml
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

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

    BrowserImportScreen(state = state, onAction = viewModel::dispatch, onNavigateBack = onNavigateBack)
}

/**
 * The last resort for a site that refuses automated fetches: show it to the user in a real browser.
 *
 * Nothing here tries to defeat the bot check — the user clears it themselves, exactly as they would
 * in Chrome. The rendered DOM is polled throughout, so the moment the real page appears the recipe
 * is picked up and the screen gets out of the way.
 *
 * Not one of the 19 designed screens; the handoff's instruction for it is to reuse screen 18's
 * header and error-banner treatment, which is what [ChefAITopAppBar] plus the accent-tinted banner
 * below give it.
 */
@Composable
fun BrowserImportScreen(
    state: BrowserImportState,
    onAction: (BrowserImportAction) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .testTag("BrowserImportScreen")
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ChefAITopAppBar(
            title = stringResource(R.string.import_recipe_title),
            navigation = ChefAINavigation.Close(onClick = { onAction(BrowserImportAction.Cancel) }),
        )
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
    // See ImportRecipeScreen's ImportErrorBanner — same reasoning, same DARK MODE FLAG.
    val hintRes = when {
        state.errorRes != null -> state.errorRes
        state.phase == BrowserImportState.Phase.AWAITING_USER -> R.string.import_recipe_browser_hint
        else -> R.string.import_recipe_browser_loading
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (state.errorRes != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(ChefAIIcons.CircleAlert),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        } else {
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatButton(
                text = stringResource(R.string.import_recipe_browser_cancel),
                onClick = { onAction(BrowserImportAction.Cancel) },
                variant = FlatButtonVariant.Ghost,
            )
            if (state.errorRes != null) {
                FlatButton(
                    text = stringResource(R.string.import_recipe_enter_manually),
                    onClick = { onAction(BrowserImportAction.EnterManually) },
                    variant = FlatButtonVariant.Ghost,
                )
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

@Preview(name = "Awaiting user — Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
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
