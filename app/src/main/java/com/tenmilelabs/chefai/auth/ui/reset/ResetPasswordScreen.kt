package com.tenmilelabs.chefai.auth.ui.reset

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.ui.AuthMetrics
import com.tenmilelabs.chefai.auth.ui.PasswordVisibilityToggle
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.navigation.ChefAINavigation
import com.tenmilelabs.chefai.core.ui.navigation.ChefAITopAppBarWithWordmark
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

@Composable
fun ResetPasswordScreen(
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ResetPasswordEvent.NavigateToForgotPassword -> onNavigateToForgotPassword()
                ResetPasswordEvent.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    ResetPasswordContent(uiState = uiState, onAction = viewModel::onAction, onNavigateBack = onNavigateBack)
}

@Composable
fun ResetPasswordContent(
    uiState: ResetPasswordUiState,
    onAction: (ResetPasswordAction) -> Unit,
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
                    .testTag("ResetPasswordScreen")
                    .padding(
                        horizontal = dimensionResource(id = R.dimen.padding_medium),
                        vertical = dimensionResource(id = R.dimen.padding_large),
                    ),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                when (uiState) {
                    ResetPasswordUiState.Validating -> Text(
                        text = stringResource(R.string.reset_password_validating),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("ResetPasswordValidating"),
                    )
                    ResetPasswordUiState.Expired -> ExpiredBody(onAction)
                    is ResetPasswordUiState.LoadFailed -> LoadFailedBody(uiState.error, onAction)
                    is ResetPasswordUiState.Editing -> EditingBody(uiState, onAction)
                    is ResetPasswordUiState.Done -> DoneBody(uiState, onAction)
                }
            }
        }
    }
}

@Composable
private fun Title(@androidx.annotation.StringRes res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(AuthMetrics.TitleToSubtitleGap))
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ExpiredBody(onAction: (ResetPasswordAction) -> Unit) {
    Title(R.string.reset_password_expired_title)
    Body(stringResource(R.string.reset_password_expired_body), Modifier.testTag("ResetPasswordExpired"))
    Spacer(Modifier.height(AuthMetrics.SubtitleToFieldsGap))
    FlatBlockButton(
        text = stringResource(R.string.reset_password_request_new_link),
        onClick = { onAction(ResetPasswordAction.RequestNewLink) },
        modifier = Modifier.testTag("ResetPasswordRequestNewLink"),
    )
}

@Composable
private fun LoadFailedBody(error: ResetPasswordError, onAction: (ResetPasswordAction) -> Unit) {
    Title(R.string.reset_password_title)
    Body(stringResource(error.messageRes()))
    Spacer(Modifier.height(AuthMetrics.SubtitleToFieldsGap))
    FlatBlockButton(
        text = stringResource(R.string.reset_password_retry),
        onClick = { onAction(ResetPasswordAction.Retry) },
    )
}

@Composable
private fun EditingBody(state: ResetPasswordUiState.Editing, onAction: (ResetPasswordAction) -> Unit) {
    Title(R.string.reset_password_title)
    Body(stringResource(R.string.reset_password_subtitle))
    Spacer(Modifier.height(AuthMetrics.SubtitleToFieldsGap))
    val transformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation()
    FlatField(
        value = state.newPassword,
        onValueChange = { onAction(ResetPasswordAction.NewPasswordChanged(it)) },
        label = stringResource(R.string.reset_password_new_label),
        leadingIcon = ChefAIIcons.Lock,
        trailing = {
            PasswordVisibilityToggle(
                isVisible = state.isPasswordVisible,
                onToggle = { onAction(ResetPasswordAction.TogglePasswordVisibility) },
            )
        },
        visualTransformation = transformation,
        errorText = state.newPasswordError?.let { stringResource(it.messageRes()) } ?: state.serverMessage,
        enabled = !state.isSubmitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag("ResetPasswordNewField"),
    )
    Spacer(Modifier.height(AuthMetrics.FieldGap))
    FlatField(
        value = state.confirmPassword,
        onValueChange = { onAction(ResetPasswordAction.ConfirmPasswordChanged(it)) },
        label = stringResource(R.string.reset_password_confirm_label),
        leadingIcon = ChefAIIcons.Lock,
        visualTransformation = transformation,
        errorText = state.confirmPasswordError?.let { stringResource(it.messageRes()) },
        enabled = !state.isSubmitting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAction(ResetPasswordAction.Submit) }),
        modifier = Modifier.fillMaxWidth().testTag("ResetPasswordConfirmField"),
    )
    state.error?.let {
        Spacer(Modifier.height(AuthMetrics.FieldGap))
        Text(
            text = stringResource(it.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(AuthMetrics.FieldGap))
    FlatBlockButton(
        text = stringResource(R.string.reset_password_submit_button),
        onClick = { onAction(ResetPasswordAction.Submit) },
        loading = state.isSubmitting,
        modifier = Modifier.testTag("ResetPasswordSubmit"),
    )
}

@Composable
private fun DoneBody(state: ResetPasswordUiState.Done, onAction: (ResetPasswordAction) -> Unit) {
    Title(R.string.reset_password_done_title)
    Body(stringResource(R.string.reset_password_done_body), Modifier.testTag("ResetPasswordDone"))
    if (state.otherDevicesSignedOut) {
        Spacer(Modifier.height(AuthMetrics.TitleToSubtitleGap))
        Body(stringResource(R.string.reset_password_done_signed_out_others))
    }
    Spacer(Modifier.height(AuthMetrics.SubtitleToFieldsGap))
    FlatBlockButton(
        text = stringResource(R.string.reset_password_sign_in),
        onClick = { onAction(ResetPasswordAction.SignIn) },
        modifier = Modifier.testTag("ResetPasswordSignIn"),
    )
}

private fun ResetPasswordFieldError.messageRes(): Int = when (this) {
    ResetPasswordFieldError.TooShort -> R.string.reset_password_error_too_short
    ResetPasswordFieldError.Mismatch -> R.string.reset_password_error_mismatch
}

private fun ResetPasswordError.messageRes(): Int = when (this) {
    ResetPasswordError.Network -> R.string.reset_password_error_network
    ResetPasswordError.Unknown -> R.string.reset_password_error_unknown
}

private class ResetPreviewStates : PreviewParameterProvider<ResetPasswordUiState> {
    override val values = sequenceOf(
        ResetPasswordUiState.Validating,
        ResetPasswordUiState.Expired,
        ResetPasswordUiState.Editing(newPassword = "hunter22!", confirmPassword = "hunter2"),
        ResetPasswordUiState.Done(otherDevicesSignedOut = true),
    )
}

@Preview(showBackground = true)
@Composable
private fun ResetPasswordLightPreview(@PreviewParameter(ResetPreviewStates::class) state: ResetPasswordUiState) {
    ChefAITheme { ResetPasswordContent(uiState = state, onAction = {}, onNavigateBack = {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResetPasswordDarkPreview(@PreviewParameter(ResetPreviewStates::class) state: ResetPasswordUiState) {
    ChefAITheme(darkTheme = true) { ResetPasswordContent(uiState = state, onAction = {}, onNavigateBack = {}) }
}
