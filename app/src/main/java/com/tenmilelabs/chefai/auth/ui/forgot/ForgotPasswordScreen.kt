package com.tenmilelabs.chefai.auth.ui.forgot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.ui.AuthMetrics
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.navigation.ChefAINavigation
import com.tenmilelabs.chefai.core.ui.navigation.ChefAITopAppBarWithWordmark
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

@Composable
fun ForgotPasswordScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ForgotPasswordEvent.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    ForgotPasswordContent(uiState = uiState, onAction = viewModel::onAction, onNavigateBack = onNavigateBack)
}

@Composable
fun ForgotPasswordContent(
    uiState: ForgotPasswordUiState,
    onAction: (ForgotPasswordAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChefAITopAppBarWithWordmark(navigation = ChefAINavigation.Back(onClick = onNavigateBack))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("ForgotPasswordScreen")
                    .padding(
                        horizontal = dimensionResource(id = R.dimen.padding_medium),
                        vertical = dimensionResource(id = R.dimen.padding_large),
                    ),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                when (uiState) {
                    is ForgotPasswordUiState.Sent -> SentBody(uiState, onAction)
                    else -> FormBody(uiState, onAction)
                }
            }
        }
    }
}

@Composable
private fun FormBody(uiState: ForgotPasswordUiState, onAction: (ForgotPasswordAction) -> Unit) {
    val submitting = uiState is ForgotPasswordUiState.Submitting
    val email = when (uiState) {
        is ForgotPasswordUiState.Editing -> uiState.email
        is ForgotPasswordUiState.Submitting -> uiState.email
        is ForgotPasswordUiState.Sent -> uiState.email
    }
    val error = (uiState as? ForgotPasswordUiState.Editing)?.error

    Text(
        text = stringResource(R.string.forgot_password_title),
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(AuthMetrics.TitleToSubtitleGap))
    Text(
        text = stringResource(R.string.forgot_password_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(AuthMetrics.SubtitleToFieldsGap))
    FlatField(
        value = email,
        onValueChange = { onAction(ForgotPasswordAction.EmailChanged(it)) },
        label = stringResource(R.string.label_email),
        placeholder = stringResource(R.string.placeholder_email),
        leadingIcon = ChefAIIcons.Mail,
        errorText = error?.let { stringResource(it.messageRes()) },
        enabled = !submitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAction(ForgotPasswordAction.Submit) }),
        modifier = Modifier.fillMaxWidth().testTag("ForgotPasswordEmailField"),
    )
    Spacer(Modifier.height(AuthMetrics.FieldGap))
    FlatBlockButton(
        text = stringResource(R.string.forgot_password_send_button),
        onClick = { onAction(ForgotPasswordAction.Submit) },
        loading = submitting,
        modifier = Modifier.testTag("ForgotPasswordSubmit"),
    )
    Spacer(Modifier.height(AuthMetrics.FieldGap))
    FlatBlockButton(
        text = stringResource(R.string.forgot_password_back_to_sign_in),
        onClick = { onAction(ForgotPasswordAction.BackToSignIn) },
        variant = FlatButtonVariant.Ghost,
    )
}

@Composable
private fun SentBody(state: ForgotPasswordUiState.Sent, onAction: (ForgotPasswordAction) -> Unit) {
    Text(
        text = stringResource(R.string.forgot_password_sent_title),
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(AuthMetrics.TitleToSubtitleGap))
    Text(
        text = stringResource(R.string.forgot_password_sent_body, state.email),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("ForgotPasswordSentBody"),
    )
    state.error?.let {
        Spacer(Modifier.height(AuthMetrics.FieldGap))
        Text(
            text = stringResource(it.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(AuthMetrics.SubtitleToFieldsGap))
    FlatBlockButton(
        text = if (state.resendSecondsLeft > 0) {
            stringResource(R.string.forgot_password_resend_countdown, state.resendSecondsLeft)
        } else {
            stringResource(R.string.forgot_password_resend)
        },
        onClick = { onAction(ForgotPasswordAction.Resend) },
        enabled = state.resendSecondsLeft == 0,
        loading = state.isResending,
        variant = FlatButtonVariant.Secondary,
        modifier = Modifier.testTag("ForgotPasswordResend"),
    )
    Spacer(Modifier.height(AuthMetrics.FieldGap))
    FlatBlockButton(
        text = stringResource(R.string.forgot_password_back_to_sign_in),
        onClick = { onAction(ForgotPasswordAction.BackToSignIn) },
        modifier = Modifier.testTag("ForgotPasswordBackToSignIn"),
    )
}

private fun ForgotPasswordError.messageRes(): Int = when (this) {
    ForgotPasswordError.InvalidEmail -> R.string.forgot_password_error_invalid_email
    ForgotPasswordError.RateLimited -> R.string.forgot_password_error_rate_limited
    ForgotPasswordError.Network -> R.string.forgot_password_error_network
    ForgotPasswordError.Unknown -> R.string.forgot_password_error_unknown
}

private class PreviewStates : androidx.compose.ui.tooling.preview.PreviewParameterProvider<ForgotPasswordUiState> {
    override val values = sequenceOf(
        ForgotPasswordUiState.Editing("alice@example.com"),
        ForgotPasswordUiState.Editing("nope", ForgotPasswordError.InvalidEmail),
        ForgotPasswordUiState.Sent("alice@example.com", resendSecondsLeft = 42),
    )
}

@Preview(showBackground = true)
@Composable
private fun ForgotPasswordLightPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(PreviewStates::class) state: ForgotPasswordUiState,
) {
    ChefAITheme { ForgotPasswordContent(uiState = state, onAction = {}, onNavigateBack = {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ForgotPasswordDarkPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(PreviewStates::class) state: ForgotPasswordUiState,
) {
    ChefAITheme(darkTheme = true) { ForgotPasswordContent(uiState = state, onAction = {}, onNavigateBack = {}) }
}
