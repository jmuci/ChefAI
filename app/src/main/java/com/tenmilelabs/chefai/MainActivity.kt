package com.tenmilelabs.chefai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.navigation.ChefAINavGraph
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.timer.FloatingRecipeTimerWidget
import com.tenmilelabs.chefai.core.util.InviteLinkParser
import com.tenmilelabs.chefai.core.util.extractSharedRecipeUrl
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingSharedUrl by mutableStateOf<String?>(null)
    private var pendingInviteToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeShareIntent(intent)
        consumeInviteIntent(intent)
        setContent {
            ChefAITheme {
                Surface(tonalElevation = 5.dp) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ChefAINavGraph(
                            modifier = Modifier.fillMaxSize(),
                            pendingSharedUrl = pendingSharedUrl,
                            onPendingSharedUrlConsumed = { pendingSharedUrl = null },
                            pendingInviteToken = pendingInviteToken,
                            onPendingInviteTokenConsumed = { pendingInviteToken = null },
                        )
                        // Lives above the nav graph, not inside it, so a timer survives
                        // navigating between screens instead of being torn down with the route.
                        FloatingRecipeTimerWidget()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
        consumeInviteIntent(intent)
    }

    /**
     * Handles a `Share` from another app (e.g. Chrome's share sheet) landing on this activity.
     *
     * The intent is emptied as it is read. `getIntent()` keeps returning the launching intent for
     * the life of the task, so every activity recreation — a rotation, a theme change, coming back
     * after process death — re-runs [onCreate] against the same shared text; without this, a URL
     * the user had already imported or dismissed would re-open the import screen underneath them
     * each time.
     */
    private fun consumeShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        // getCharSequenceExtra: some senders put a Spanned in EXTRA_TEXT, which getStringExtra
        // returns as null.
        pendingSharedUrl = extractSharedRecipeUrl(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
        intent.removeExtra(Intent.EXTRA_TEXT)
    }

    /**
     * Handles an App Link (`https://chefai.app/invite?token=...`) landing on this activity.
     *
     * Mirrors [consumeShareIntent]: the token is read once and the data URI is cleared from the
     * intent as it's read, for the same reason — `getIntent()` keeps returning this same launching
     * intent for the life of the task, so every recreation (rotation, theme change, process death)
     * re-runs [onCreate] against it; without clearing it, a link the user already resolved (joined,
     * declined, or dismissed) would re-open the accept screen underneath them every time.
     */
    private fun consumeInviteIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        pendingInviteToken = InviteLinkParser.extractToken(intent.data?.toString())
        intent.data = null
    }
}
