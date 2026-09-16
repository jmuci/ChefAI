package com.tenmilelabs.chefai.household.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvitePreview

@Composable
fun AcceptInviteScreen(
    onNavigateToHousehold: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
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
        modifier = modifier,
    )
}

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
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is AcceptInviteUiState.EnterCode -> EnterCodeContent(
            code = uiState.code,
            onCodeChanged = onCodeChanged,
            onSubmitCode = onSubmitCode,
            modifier = modifier,
        )
        AcceptInviteUiState.Loading -> LoadingContent(modifier = modifier)
        is AcceptInviteUiState.Preview -> PreviewContent(
            preview = uiState.preview,
            onAccept = onAccept,
            modifier = modifier,
        )
        AcceptInviteUiState.InvalidOrExpired -> MessageContent(
            title = stringResource(R.string.accept_invite_invalid_title),
            subtitle = stringResource(R.string.accept_invite_invalid_subtitle),
            buttonText = stringResource(R.string.accept_invite_try_again_button),
            onButtonClick = onRetry,
            modifier = modifier,
        )
        AcceptInviteUiState.AlreadyInAHousehold -> MessageContent(
            title = stringResource(R.string.accept_invite_already_title),
            subtitle = stringResource(R.string.accept_invite_already_subtitle),
            buttonText = stringResource(R.string.accept_invite_joined_button),
            onButtonClick = onNavigateToHousehold,
            modifier = modifier,
        )
        AcceptInviteUiState.Joined -> MessageContent(
            title = stringResource(R.string.accept_invite_joined_title),
            subtitle = stringResource(R.string.accept_invite_joined_subtitle),
            buttonText = stringResource(R.string.accept_invite_joined_button),
            onButtonClick = onNavigateToHousehold,
            modifier = modifier,
        )
        AcceptInviteUiState.RequiresSignIn -> RequiresSignInContent(
            onNavigateToLogin = onNavigateToLogin,
            onNavigateToRegister = onNavigateToRegister,
            modifier = modifier,
        )
        is AcceptInviteUiState.Error -> MessageContent(
            title = stringResource(R.string.household_error_title),
            subtitle = uiState.message,
            buttonText = stringResource(R.string.household_retry_button),
            onButtonClick = onRetry,
            modifier = modifier,
        )
    }
}

@Composable
private fun EnterCodeContent(
    code: String,
    onCodeChanged: (String) -> Unit,
    onSubmitCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimensionResource(R.dimen.padding_large)),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.accept_invite_code_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = dimensionResource(R.dimen.padding_medium)),
        )
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChanged,
            label = { Text(stringResource(R.string.accept_invite_code_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensionResource(R.dimen.padding_medium)),
        )
        Button(
            onClick = onSubmitCode,
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.accept_invite_code_button))
        }
    }
}

@Composable
private fun PreviewContent(
    preview: HouseholdInvitePreview,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimensionResource(R.dimen.padding_large)),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = preview.householdName, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.accept_invite_invited_by, preview.inviterDisplayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                top = dimensionResource(R.dimen.padding_small),
                bottom = dimensionResource(R.dimen.padding_large),
            ),
        )
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.accept_invite_accept_button))
        }
    }
}

@Composable
private fun RequiresSignInContent(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimensionResource(R.dimen.padding_large)),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.accept_invite_signin_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.accept_invite_signin_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                top = dimensionResource(R.dimen.padding_small),
                bottom = dimensionResource(R.dimen.padding_large),
            ),
        )
        Button(onClick = onNavigateToLogin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.accept_invite_login_button))
        }
        OutlinedButton(
            onClick = onNavigateToRegister,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(R.dimen.padding_small)),
        ) {
            Text(stringResource(R.string.accept_invite_register_button))
        }
    }
}

@Composable
private fun MessageContent(
    title: String,
    subtitle: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimensionResource(R.dimen.padding_large)),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                top = dimensionResource(R.dimen.padding_small),
                bottom = dimensionResource(R.dimen.padding_large),
            ),
        )
        Button(onClick = onButtonClick, modifier = Modifier.fillMaxWidth()) {
            Text(buttonText)
        }
    }
}

@Preview(name = "Accept invite – enter code, light", showBackground = true)
@Composable
private fun AcceptInviteEnterCodePreview() {
    ChefAITheme {
        Surface {
            AcceptInviteContent(
                uiState = AcceptInviteUiState.EnterCode(),
                onCodeChanged = {},
                onSubmitCode = {},
                onAccept = {},
                onRetry = {},
                onNavigateToHousehold = {},
                onNavigateToLogin = {},
                onNavigateToRegister = {},
            )
        }
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
        Surface {
            AcceptInviteContent(
                uiState = AcceptInviteUiState.Preview(
                    preview = HouseholdInvitePreview("The Test Kitchen", "Chef Owner"),
                    token = "abc",
                ),
                onCodeChanged = {},
                onSubmitCode = {},
                onAccept = {},
                onRetry = {},
                onNavigateToHousehold = {},
                onNavigateToLogin = {},
                onNavigateToRegister = {},
            )
        }
    }
}

@Preview(name = "Accept invite – requires sign in, light", showBackground = true)
@Composable
private fun AcceptInviteRequiresSignInPreview() {
    ChefAITheme {
        Surface {
            AcceptInviteContent(
                uiState = AcceptInviteUiState.RequiresSignIn,
                onCodeChanged = {},
                onSubmitCode = {},
                onAccept = {},
                onRetry = {},
                onNavigateToHousehold = {},
                onNavigateToLogin = {},
                onNavigateToRegister = {},
            )
        }
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
        Surface {
            AcceptInviteContent(
                uiState = AcceptInviteUiState.InvalidOrExpired,
                onCodeChanged = {},
                onSubmitCode = {},
                onAccept = {},
                onRetry = {},
                onNavigateToHousehold = {},
                onNavigateToLogin = {},
                onNavigateToRegister = {},
            )
        }
    }
}
