package com.tenmilelabs.chefai.auth.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.AuthToken
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.domain.model.User
import com.tenmilelabs.chefai.core.ui.components.flat.CircleAvatar
import com.tenmilelabs.chefai.core.ui.components.flat.DISABLED_ALPHA
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.components.flat.SectionRule
import com.tenmilelabs.chefai.core.ui.components.flat.avatarInitials
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch

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

/** The panel's geometry — screen 09 in the handoff. */
private object MenuMetrics {
    /** `width: 228px`. Fixed, not wrap-content: the panel is a column of its own rhythm. */
    val PanelWidth = 228.dp

    /** The avatar, and the one circle in the whole design system. */
    val AvatarSize = 40.dp

    /** `padding: var(--space-3)` in the header block and along each row. */
    val Padding = 12.dp

    /** `min-height: 48px` per row — above the system's 44dp floor, as the design draws it. */
    val RowMinHeight = 48.dp

    /** `width/height: 18` on the Lucide glyph in a row; `gap: 12px` to its label. */
    val RowIconSize = 18.dp
    val RowIconGap = 12.dp

    /** `margin-top: 3px` between "Signed in as" and the display name. */
    val HeaderNameGap = 3.dp

    /** The glyph inside the avatar when there is no account to take initials from. */
    val AvatarIconSize = 20.dp

    /**
     * Breathing room around the panel inside the popup window, so `--shadow-lg` has somewhere to
     * fall.
     *
     * A `Popup` is its own window, sized to its content, and a shadow is drawn *outside* the
     * content bounds — with the panel flush against the window it is clipped away entirely and the
     * panel renders with a border and nothing else. `--shadow-lg` is `0 12px 32px`, so 24dp clears
     * the blur on every side. [BelowAnchorEndAligned] subtracts it again, so the panel itself still
     * lands exactly under the avatar.
     */
    val ShadowBleed = 24.dp
}

/**
 * The user profile menu — the avatar in the header, and the panel it opens.
 *
 * Deliberately **not** `DropdownMenu`. Material's menu is a rounded, tonally-elevated `Surface`
 * with its own padding and item heights; this design's panel is a flat rectangle held by a 2dp ink
 * border, ruled internally, with a single drop shadow. Overriding Material's surface leaves its
 * insets and minimum item metrics behind, so the panel is a plain [Popup].
 *
 * In light the shadow and the border work together; in **dark the border carries it alone** — the
 * handoff is explicit that shadows read as noise on a dark ground, so
 * [chefColors][com.tenmilelabs.chefai.core.ui.theme.ChefColors.panelShadowElevation] is 0dp there
 * and the 2dp border is the only thing separating the panel from what is behind it.
 *
 * All three [UserSession] states render as the same panel — the design styles the guest and
 * loading variants identically, they just hold different rows.
 *
 * Not implemented here: the handoff draws screen 09 with the content behind the panel dimmed to
 * 30%. That scrim covers the whole screen, which this component — living inside the header's
 * actions row — cannot reach. It belongs with whoever owns the Scaffold.
 *
 * @param onLogin Callback invoked to open the login screen
 * @param onLogout Callback invoked after successful logout
 * @param onSettings Callback invoked to open the settings screen. Offered to signed-in and guest
 *   sessions alike — the settings behind it are device preferences, not account properties.
 * @param onHousehold Callback invoked to open the household screen. Authenticated only — an
 *   anonymous session has no account to share a household with.
 * @param modifier Modifier for styling
 * @param viewModel ViewModel for handling logout logic
 */
@Composable
fun UserProfileMenu(
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    onSettings: () -> Unit = {},
    onHousehold: () -> Unit = {},
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val userSession by rememberUserSession()
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        BelowAnchorEndAligned(with(density) { MenuMetrics.ShadowBleed.roundToPx() })
    }

    Box(modifier = modifier) {
        ProfileAvatar(
            session = userSession,
            onClick = { expanded = true },
            modifier = Modifier.testTag("UserProfileMenu"),
        )

        if (expanded) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                MenuPanel {
                    when (val session = userSession) {
                        is UserSession.Authenticated -> AuthenticatedRows(
                            displayName = session.user.displayName,
                            onHousehold = {
                                expanded = false
                                onHousehold()
                            },
                            onSettings = {
                                expanded = false
                                onSettings()
                            },
                            onLogout = {
                                expanded = false
                                viewModel.logout(onLogout)
                            },
                        )

                        is UserSession.Anonymous -> AnonymousRows(
                            onLogin = {
                                expanded = false
                                onLogin()
                            },
                            onSettings = {
                                expanded = false
                                onSettings()
                            },
                        )

                        is UserSession.Loading -> MenuRow(
                            label = stringResource(R.string.profile_menu_loading),
                            icon = null,
                            enabled = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}

/**
 * The panel itself: 228dp of ground, a 2dp ink border, `--shadow-lg`, zero radius.
 *
 * The border is `onBackground` — full-strength ink, not the divider. That is what makes this read
 * as a panel lifted off the page rather than as another ruled section of it, and it is the part
 * that has to survive into dark on its own.
 */
@Composable
private fun MenuPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(MenuMetrics.ShadowBleed)
            .width(MenuMetrics.PanelWidth)
            .shadow(
                elevation = MaterialTheme.chefColors.panelShadowElevation,
                shape = RectangleShape,
                // --shadow-lg is cast in #2d2b2b, the deepest neutral — not pure black.
                ambientColor = MaterialTheme.chefColors.neutral.s900,
                spotColor = MaterialTheme.chefColors.neutral.s900,
            )
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = MaterialTheme.chefColors.sectionRuleWidth,
                color = MaterialTheme.colorScheme.onBackground,
            ),
        content = { content() },
    )
}

@Composable
private fun AuthenticatedRows(
    displayName: String,
    onHousehold: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    MenuHeader(displayName = displayName)
    MenuRow(
        label = stringResource(R.string.household_menu_item),
        icon = ChefAIIcons.Users,
        onClick = onHousehold,
        modifier = Modifier.testTag("HouseholdMenuItem"),
    )
    RowRule()
    MenuRow(
        label = stringResource(R.string.settings_menu_item),
        icon = ChefAIIcons.Settings,
        onClick = onSettings,
        modifier = Modifier.testTag("SettingsMenuItem"),
    )
    RowRule()
    MenuRow(
        label = stringResource(R.string.profile_menu_logout),
        icon = ChefAIIcons.LogOut,
        // accentText resolves to accent-700 in light — the design's Logout color, and the step
        // that is legible at 14px. In a mono palette the deep accent step is what carries
        // destructive intent; there is no error red to reach for.
        contentColor = MaterialTheme.chefColors.accentText,
        onClick = onLogout,
        modifier = Modifier.testTag("LogoutMenuItem"),
    )
}

/**
 * The guest panel. Same header block, same rows, same rules — the design is explicit that the
 * anonymous variant is styled identically and only differs in what it holds.
 *
 * There is no Household row: an anonymous session has no account to share one with.
 */
@Composable
private fun AnonymousRows(
    onLogin: () -> Unit,
    onSettings: () -> Unit,
) {
    MenuHeader(displayName = stringResource(R.string.profile_menu_guest))
    MenuRow(
        label = stringResource(R.string.profile_menu_login_register),
        icon = ChefAIIcons.CircleUserRound,
        onClick = onLogin,
        modifier = Modifier.testTag("LoginMenuItem"),
    )
    RowRule()
    MenuRow(
        label = stringResource(R.string.settings_menu_item),
        icon = ChefAIIcons.Settings,
        onClick = onSettings,
        modifier = Modifier.testTag("SettingsMenuItem"),
    )
}

/** "SIGNED IN AS" over the display name, closed by a 2dp rule. */
@Composable
private fun MenuHeader(displayName: String) {
    Column(modifier = Modifier.padding(MenuMetrics.Padding)) {
        Text(
            // labelSmall carries the uppercase tracking but not the transform — see Type.kt.
            text = stringResource(R.string.profile_menu_signed_in_as).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = displayName,
            // titleMedium is the design's 15px/800.
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MenuMetrics.HeaderNameGap),
        )
    }
    SectionRule()
}

/**
 * One row: 18dp glyph, 12dp gap, 14px label, 48dp minimum.
 *
 * The last row in a panel carries no rule beneath it — the panel's own border closes the column.
 */
@Composable
private fun MenuRow(
    label: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MenuMetrics.RowMinHeight)
            // 45% on the whole row is the design's disabled treatment; it stands in for "inactive",
            // not for a paler color.
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .flatClickable(onClick = onClick, enabled = enabled, role = Role.Button)
            .padding(horizontal = MenuMetrics.Padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MenuMetrics.RowIconGap),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(MenuMetrics.RowIconSize),
            )
        }
        Text(
            text = label,
            // bodyLarge is the design's 14px body.
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The 40dp avatar that opens the menu.
 *
 * Signed in, it is [CircleAvatar] — the system's one exception to zero radius, round because it is
 * an avatar and not a container — or the account's own photo when it has one, clipped to the same
 * circle.
 *
 * Guest and loading have no designed treatment, so rather than invent one they take the neutral
 * fill and a user glyph: visibly not an account, and not a color this file made up.
 */
@Composable
private fun ProfileAvatar(
    session: UserSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val authenticated = session as? UserSession.Authenticated
    val avatarUrl = authenticated?.user?.avatarUrl.orEmpty()

    Box(
        modifier = modifier
            .size(MenuMetrics.AvatarSize)
            .clip(CircleShape)
            .flatClickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        when {
            avatarUrl.isNotEmpty() -> AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                placeholder = painterResource(R.drawable.ic_img_placeholder),
                error = painterResource(R.drawable.ic_img_error),
                contentDescription = stringResource(R.string.user_profile_avatar_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            authenticated != null -> CircleAvatar(
                initials = avatarInitials(authenticated.user.displayName),
                size = MenuMetrics.AvatarSize,
            )

            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(ChefAIIcons.CircleUserRound),
                    contentDescription = stringResource(R.string.profile_menu_open),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(MenuMetrics.AvatarIconSize),
                )
            }
        }
    }
}

/**
 * Puts the panel directly under the avatar, right edges flush, then clamps it inside the window.
 *
 * `Popup`'s alignment-based providers position the panel *over* its anchor; the design anchors it
 * below. The clamp matters because the avatar sits at the trailing edge of the header, so an
 * un-clamped 228dp panel would hang off-screen in RTL.
 */
private class BelowAnchorEndAligned(private val bleedPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // popupContentSize includes MenuMetrics.ShadowBleed on every side; the panel's own edges
        // sit one bleed inside it, so every edge below is corrected by it.
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width + bleedPx
        } else {
            anchorBounds.left - bleedPx
        }
        return IntOffset(
            x = x.coerceIn(
                -bleedPx,
                (windowSize.width - popupContentSize.width + bleedPx).coerceAtLeast(-bleedPx),
            ),
            y = (anchorBounds.bottom - bleedPx).coerceIn(
                -bleedPx,
                (windowSize.height - popupContentSize.height + bleedPx).coerceAtLeast(-bleedPx),
            ),
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────────────────────
// The panel is previewed directly rather than through UserProfileMenu, which needs Hilt.

private val PreviewUser = User(
    uuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    displayName = "JM Muci",
    email = "jm@example.com",
    avatarUrl = "",
)

private val PreviewAuthenticated = UserSession.Authenticated(
    user = PreviewUser,
    authToken = AuthToken(accessToken = "", refreshToken = "", expiresAt = 0L),
)

private val PreviewAnonymous =
    UserSession.Anonymous(UUID.fromString("00000000-0000-0000-0000-000000000002"))

@Composable
private fun PreviewPanel(session: UserSession) {
    Box {
        MenuPanel {
            when (session) {
                is UserSession.Authenticated -> AuthenticatedRows(
                    displayName = session.user.displayName,
                    onHousehold = {},
                    onSettings = {},
                    onLogout = {},
                )

                is UserSession.Anonymous -> AnonymousRows(onLogin = {}, onSettings = {})

                is UserSession.Loading -> MenuRow(
                    label = stringResource(R.string.profile_menu_loading),
                    icon = null,
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}

@Preview(name = "Profile menu \u2014 authenticated", showBackground = true)
@Preview(
    name = "Profile menu \u2014 authenticated, dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun UserProfileMenuAuthenticatedPreview() {
    ChefAITheme { PreviewPanel(PreviewAuthenticated) }
}

@Preview(name = "Profile menu \u2014 anonymous", showBackground = true)
@Preview(
    name = "Profile menu \u2014 anonymous, dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun UserProfileMenuAnonymousPreview() {
    ChefAITheme { PreviewPanel(PreviewAnonymous) }
}

@Preview(name = "Profile menu \u2014 loading", showBackground = true)
@Preview(
    name = "Profile menu \u2014 loading, dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun UserProfileMenuLoadingPreview() {
    ChefAITheme { PreviewPanel(UserSession.Loading) }
}

@Preview(name = "Profile avatar", showBackground = true)
@Preview(name = "Profile avatar, dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ProfileAvatarPreview() {
    ChefAITheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileAvatar(session = PreviewAuthenticated, onClick = {})
            ProfileAvatar(session = PreviewAnonymous, onClick = {})
        }
    }
}
