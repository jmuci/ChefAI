package com.tenmilelabs.chefai.core.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.ui.UserProfileMenu
import com.tenmilelabs.chefai.core.ui.sync.SyncStatusIndicator


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefAITopAppBar(
    @StringRes titleResId: Int,
    onNavigationClick: (() -> Unit)? = null,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {}
) {

    return CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        title = {
            Text(
                stringResource(titleResId),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onNavigationClick != null) {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Localized description"
                    )
                }
            }
        },
        actions = {
            // Sync status indicator (hidden when idle)
            SyncStatusIndicator()
            // User profile menu on the right side
            UserProfileMenu(
                onLogin = onLogin,
                onLogout = onLogout
            )
        },
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    )
}

@Preview
@Composable
fun ChefAITopBarPreview() {
    ChefAITopAppBar(R.string.app_name,)
}