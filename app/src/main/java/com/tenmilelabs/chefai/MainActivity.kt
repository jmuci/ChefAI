package com.tenmilelabs.chefai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.navigation.ChefAINavGraph
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChefAITheme {
                Surface(tonalElevation = 5.dp) {
                    ChefAINavGraph(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
