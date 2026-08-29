package com.tenmilelabs.chefai.auth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for handling user profile actions like logout.
 */
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    fun logout(onLogoutComplete: () -> Unit = {}) {
        viewModelScope.launch {
            sessionManager.logout()
            onLogoutComplete()
        }
    }
}

/**
 * A reusable user profile menu component that shows the current user
 * and provides logout functionality.
 *
 * @param onLogout Callback invoked after successful logout
 * @param onSettings Callback invoked to open the settings screen. Offered to signed-in and guest
 *   sessions alike — the settings behind it are device preferences, not account properties.
 * @param modifier Modifier for styling
 * @param viewModel ViewModel for handling logout logic
 */
@Composable
fun UserProfileMenu(
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val userSession by rememberUserSession()
    val coroutineScope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.testTag("UserProfileMenu")) {
        IconButton(onClick = { expanded = true }) {
            if (userSession is UserSession.Authenticated && (userSession as UserSession.Authenticated).user.avatarUrl.isNotEmpty()) {
                val user = (userSession as UserSession.Authenticated).user
                timber.log.Timber.tag("UserProfileMenu").d(
                    "🖼️ UserProfileMenu building ImageRequest:\n" +
                    "  User: ${user.displayName}\n" +
                    "  Avatar URL: ${user.avatarUrl}\n" +
                    "  URL is null/empty: ${user.avatarUrl.isEmpty()}"
                )
                val imageRequest = ImageRequest.Builder(LocalContext.current)
                    .data(user.avatarUrl)
                    .crossfade(true)
                    .listener(
                        onSuccess = { _, result -> timber.log.Timber.tag("UserProfileMenu").d("✅ Avatar loading SUCCESS: ${user.avatarUrl}") },
                        onError = { _, result -> timber.log.Timber.tag("UserProfileMenu").e("❌ Avatar loading ERROR: ${user.avatarUrl}, error: ${result.throwable}") },
                        onCancel = { timber.log.Timber.tag("UserProfileMenu").d("⏸️ Avatar loading CANCELLED: ${user.avatarUrl}") }
                    )
                    .build()
                AsyncImage(
                    model = imageRequest,
                    placeholder = painterResource(R.drawable.ic_img_placeholder),
                    error = painterResource(R.drawable.ic_img_error),
                    contentDescription = stringResource(R.string.user_profile_avatar_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "User profile"
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Show user info if authenticated
            when (val session = userSession) {
                is UserSession.Authenticated -> {
                    val user = session.user

                    // User name header (non-clickable)
                    DropdownMenuItem(
                        text = { Text(text = user.displayName) },
                        onClick = { },
                        enabled = false
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_menu_item)) },
                        onClick = {
                            expanded = false
                            onSettings()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.testTag("SettingsMenuItem")
                    )

                    // Logout option
                    DropdownMenuItem(
                        text = { Text("Logout") },
                        onClick = {
                            expanded = false
                            coroutineScope.launch {
                                viewModel.logout {
                                    onLogout()
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.testTag("LogoutMenuItem")
                    )
                }

                is UserSession.Anonymous -> {
                    DropdownMenuItem(
                        text = { Text("Guest") },
                        onClick = { },
                        enabled = false
                    )
                    DropdownMenuItem(
                        text = { Text("Log in / Register") },
                        onClick = {
                            onLogin()
                            expanded = false
                        },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_menu_item)) },
                        onClick = {
                            expanded = false
                            onSettings()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.testTag("SettingsMenuItem")
                    )

                }

                is UserSession.Loading -> {
                    DropdownMenuItem(
                        text = { Text("Loading...") },
                        onClick = { },
                        enabled = false
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UserProfileMenuPreview() {
    ChefAITheme {
        // Note: This preview won't work properly without Hilt,
        // but demonstrates the UI structure
        UserProfileMenu()
    }
}
