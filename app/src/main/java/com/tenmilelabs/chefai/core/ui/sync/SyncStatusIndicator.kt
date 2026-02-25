package com.tenmilelabs.chefai.core.ui.sync

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Small sync-status icon displayed in the TopAppBar.
 *
 * | State    | Icon            | Behaviour                  |
 * |----------|-----------------|----------------------------|
 * | Idle     | —               | Hidden (keeps bar clean)   |
 * | Syncing  | CloudSync       | Slow rotation animation    |
 * | Synced   | CloudDone       | Static, primary colour     |
 * | Error    | SyncProblem     | Static, error colour       |
 * | Offline  | CloudOff        | Static, tertiary colour    |
 */
@Composable
fun SyncStatusIndicator(
    modifier: Modifier = Modifier,
    viewModel: SyncStatusViewModel = hiltViewModel()
) {
    val status by viewModel.displayStatus.collectAsStateWithLifecycle()

    when (status) {
        is SyncDisplayStatus.Idle -> {
            // No icon when idle — keeps the TopAppBar clean
        }
        is SyncDisplayStatus.Syncing -> {
            RotatingSyncIcon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = "Syncing",
                modifier = modifier
            )
        }
        is SyncDisplayStatus.Synced -> {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = "Synced",
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier
            )
        }
        is SyncDisplayStatus.Error -> {
            Icon(
                imageVector = Icons.Default.SyncProblem,
                contentDescription = "Sync error",
                tint = MaterialTheme.colorScheme.error,
                modifier = modifier
            )
        }
        is SyncDisplayStatus.Offline -> {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Offline",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = modifier
            )
        }
    }
}

/**
 * A sync icon that gently rotates while syncing is in progress.
 */
@Composable
private fun RotatingSyncIcon(
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncRotation"
    )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.rotate(rotation)
    )
}
