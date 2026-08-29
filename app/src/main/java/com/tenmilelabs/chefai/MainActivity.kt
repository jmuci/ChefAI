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
import com.tenmilelabs.chefai.core.util.extractSharedRecipeUrl
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingSharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeShareIntent(intent)
        setContent {
            ChefAITheme {
                Surface(tonalElevation = 5.dp) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ChefAINavGraph(
                            modifier = Modifier.fillMaxSize(),
                            pendingSharedUrl = pendingSharedUrl,
                            onPendingSharedUrlConsumed = { pendingSharedUrl = null },
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
    }

    /** Handles a `Share` from another app (e.g. Chrome's share sheet) landing on this activity. */
    private fun consumeShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        pendingSharedUrl = extractSharedRecipeUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
