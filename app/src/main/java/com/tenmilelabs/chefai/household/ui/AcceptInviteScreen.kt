package com.tenmilelabs.chefai.household.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.navigation.ChefAINavigation
import com.tenmilelabs.chefai.core.ui.navigation.ChefAITopAppBar
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvitePreview

@Composable
fun AcceptInviteScreen(
    onNavigateToHousehold: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AcceptInviteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AcceptInviteContent(
        uiState = uiState,
        onCodeChanged = viewModel::onCodeChanged,
        onSubmitCode = viewModel::onSubmitCode,
        onAccept = viewModel::onAccept,
        onRetry = viewModel::onRetry,
        onNavigateToHousehold = onNavigateToHousehold,
        onNavigateToLogin = onNavigateToLogin,
        onNavigateToRegister = onNavigateToRegister,
        onClose = onClose,
        modifier = modifier,
    )
}

/**
 * Screen 12's shell — close (X) header + "Invite" — wraps every [AcceptInviteUiState]. The handoff
 * draws it once for the *preview* (not-yet-joined) state and says every other
 * [com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome] state reuses it with copy
 * swapped in from `strings.xml`; [EnterCode] isn't one of the 19 handoff screens (it's the manual
 * fallback [onNavigateToLogin]/an App Link skip), so it takes the same shell for consistency rather
 * than a bespoke look.
 */
@Composable
fun AcceptInviteContent(
    uiState: AcceptInviteUiState,
    onCodeChanged: (String) -> Unit,
    onSubmitCode: () -> Unit,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToHousehold: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ChefAITopAppBar(
            title = stringResource(R.string.accept_invite_header_title),
            navigation = ChefAINavigation.Close(onClose),
        )

        when (uiState) {
            is AcceptInviteUiState.EnterCode -> EnterCodeContent(
                code = uiState.code,
                onCodeChanged = onCodeChanged,
                onSubmitCode = onSubmitCode,
            )
            AcceptInviteUiState.Loading -> LoadingContent(modifier = Modifier.weight(1f))
            is AcceptInviteUiState.Preview -> PreviewContent(
                preview = uiState.preview,
                onAccept = onAccept,
                onTryAnotherCode = onRetry,
            )
            AcceptInviteUiState.InvalidOrExpired -> MessageContent(
                title = stringResource(R.string.accept_invite_invalid_title),
                subtitle = stringResource(R.string.accept_invite_invalid_subtitle),
                buttonText = stringResource(R.string.accept_invite_try_again_button),
                onButtonClick = onRetry,
            )
            AcceptInviteUiState.AlreadyInAHousehold -> MessageContent(
                title = stringResource(R.string.accept_invite_already_title),
                subtitle = stringResource(R.string.accept_invite_already_subtitle),
                buttonText = stringResource(R.string.accept_invite_joined_button),
                onButtonClick = onNavigateToHousehold,
            )
            AcceptInviteUiState.Joined -> MessageContent(
                title = stringResource(R.string.accept_invite_joined_title),
                subtitle = stringResource(R.string.accept_invite_joined_subtitle),
                buttonText = stringResource(R.string.accept_invite_joined_button),
                onButtonClick = onNavigateToHousehold,
            )
            AcceptInviteUiState.RequiresSignIn -> RequiresSignInContent(
                onNavigateToLogin = onNavigateToLogin,
                onNavigateToRegister = onNavigateToRegister,
            )
            is AcceptInviteUiState.Error -> MessageContent(
                title = stringResource(R.string.household_error_title),
                subtitle = uiState.message,
                buttonText = stringResource(R.string.household_retry_button),
                onButtonClick = onRetry,
            )
        }
    }
}

@Composable
private fun ColumnScope.EnterCodeContent(
    code: String,
    onCodeChanged: (String) -> Unit,
    onSubmitCode: () -> Unit,
) {
    Column(modifier = Modifier.weight(1f).padding(16.dp)) {
        Text(
            text = stringResource(R.string.accept_invite_code_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        FlatField(
            value = code,
            onValueChange = onCodeChanged,
            label = stringResource(R.string.accept_invite_code_label),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        FlatBlockButton(
            text = stringResource(R.string.accept_invite_code_button),
            onClick = onSubmitCode,
            enabled = code.isNotBlank(),
        )
    }
}

/**
 * Not drawn here: the handoff's member-tile stack ("N people already cooking here") and the
 * bordered invite-code box. [HouseholdInvitePreview] — the unauthenticated-safe preview payload
 * from `GET /households/invites/preview` — carries only [HouseholdInvitePreview.householdName] and
 * [HouseholdInvitePreview.inviterDisplayName]; there is no member count or human-readable code in
 * that response to source them from. Dropped rather than faked, same call this codebase already
 * makes for the Import screen's "Recently imported" section when its data source is missing.
 */
@Composable
private fun ColumnScope.PreviewContent(
    preview: HouseholdInvitePreview,
    onAccept: () -> Unit,
    onTryAnotherCode: () -> Unit,
) {
    Column(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Text(
                text = stringResource(R.string.accept_invite_invited_by, preview.inviterDisplayName)
                    .uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.chefColors.accentText,
            )
            Text(
                text = preview.householdName,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.accept_invite_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlatBlockButton(
                text = stringResource(R.string.accept_invite_accept_button),
                onClick = onAccept,
            )
            FlatButton(
                text = stringResource(R.string.accept_invite_try_again_button),
                onClick = onTryAnotherCode,
                variant = FlatButtonVariant.Ghost,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ColumnScope.RequiresSignInContent(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    Column(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Text(
                text = stringResource(R.string.accept_invite_signin_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.accept_invite_signin_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlatBlockButton(
                text = stringResource(R.string.accept_invite_login_button),
                onClick = onNavigateToLogin,
            )
            FlatBlockButton(
                text = stringResource(R.string.accept_invite_register_button),
                onClick = onNavigateToRegister,
                variant = FlatButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun ColumnScope.MessageContent(
    title: String,
    subtitle: String,
    buttonText: String,
    onButtonClick: () -> Unit,
) {
    Column(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        FlatBlockButton(
            text = buttonText,
            onClick = onButtonClick,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Accept invite – enter code, light", showBackground = true)
@Composable
private fun AcceptInviteEnterCodePreview() {
    ChefAITheme {
        AcceptInviteContent(
            uiState = AcceptInviteUiState.EnterCode(),
            onCodeChanged = {},
            onSubmitCode = {},
            onAccept = {},
            onRetry = {},
            onNavigateToHousehold = {},
            onNavigateToLogin = {},
            onNavigateToRegister = {},
            onClose = {},
        )
    }
}

@Preview(
    name = "Accept invite – preview, dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AcceptInvitePreviewContentPreview() {
    ChefAITheme {
        AcceptInviteContent(
            uiState = AcceptInviteUiState.Preview(
                preview = HouseholdInvitePreview("The Muci Kitchen", "Ana Muci"),
                token = "abc",
            ),
            onCodeChanged = {},
            onSubmitCode = {},
            onAccept = {},
            onRetry = {},
            onNavigateToHousehold = {},
            onNavigateToLogin = {},
            onNavigateToRegister = {},
            onClose = {},
        )
    }
}

@Preview(name = "Accept invite – requires sign in, light", showBackground = true)
@Composable
private fun AcceptInviteRequiresSignInPreview() {
    ChefAITheme {
        AcceptInviteContent(
            uiState = AcceptInviteUiState.RequiresSignIn,
            onCodeChanged = {},
            onSubmitCode = {},
            onAccept = {},
            onRetry = {},
            onNavigateToHousehold = {},
            onNavigateToLogin = {},
            onNavigateToRegister = {},
            onClose = {},
        )
    }
}

@Preview(
    name = "Accept invite – invalid, dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AcceptInviteInvalidPreview() {
    ChefAITheme {
        AcceptInviteContent(
            uiState = AcceptInviteUiState.InvalidOrExpired,
            onCodeChanged = {},
            onSubmitCode = {},
            onAccept = {},
            onRetry = {},
            onNavigateToHousehold = {},
            onNavigateToLogin = {},
            onNavigateToRegister = {},
            onClose = {},
        )
    }
}

@Preview(name = "Accept invite – already in a household, light", showBackground = true)
@Composable
private fun AcceptInviteAlreadyPreview() {
    ChefAITheme {
        AcceptInviteContent(
            uiState = AcceptInviteUiState.AlreadyInAHousehold,
            onCodeChanged = {},
            onSubmitCode = {},
            onAccept = {},
            onRetry = {},
            onNavigateToHousehold = {},
            onNavigateToLogin = {},
            onNavigateToRegister = {},
            onClose = {},
        )
    }
}

@Preview(
    name = "Accept invite – joined, dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AcceptInviteJoinedPreview() {
    ChefAITheme {
        AcceptInviteContent(
            uiState = AcceptInviteUiState.Joined,
            onCodeChanged = {},
            onSubmitCode = {},
            onAccept = {},
            onRetry = {},
            onNavigateToHousehold = {},
            onNavigateToLogin = {},
            onNavigateToRegister = {},
            onClose = {},
        )
    }
}
