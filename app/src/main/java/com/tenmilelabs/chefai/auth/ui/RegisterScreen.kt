package com.tenmilelabs.chefai.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.navigation.ChefAINavigation
import com.tenmilelabs.chefai.core.ui.navigation.ChefAITopAppBarWithWordmark
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToAcceptInvite: (String) -> Unit = {},
    onNavigateBack: () -> Unit = onNavigateToLogin,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Handle UI events
    @Suppress("CompositionLocalAccess")
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is RegisterUiEvent.ShowSnackbar -> {
                    snackbarHostState?.showSnackbar(
                        message = context.getString(event.message),
                        duration = SnackbarDuration.Short
                    )
                }
                is RegisterUiEvent.ShowSnackbarText -> {
                    snackbarHostState?.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                RegisterUiEvent.NavigateToHome -> {
                    onNavigateToHome()
                }
                RegisterUiEvent.NavigateToLogin -> {
                    onNavigateToLogin()
                }
                is RegisterUiEvent.NavigateToAcceptInvite -> {
                    onNavigateToAcceptInvite(event.token)
                }
            }
        }
    }

    RegisterScreenContent(
        uiState = uiState,
        focusManager = focusManager,
        scrollState = scrollState,
        onUsernameChange = viewModel::onUsernameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onPasswordVisibilityToggle = viewModel::onPasswordVisibilityToggle,
        onConfirmPasswordVisibilityToggle = viewModel::onConfirmPasswordVisibilityToggle,
        onRegisterClick = viewModel::onRegisterClick,
        onLoginClick = viewModel::onLoginClick,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
fun RegisterScreenContent(
    uiState: RegisterUiState,
    focusManager: FocusManager,
    scrollState: androidx.compose.foundation.ScrollState,
    onUsernameChange: (String) -> Unit = {},
    onEmailChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onConfirmPasswordChange: (String) -> Unit = {},
    onPasswordVisibilityToggle: () -> Unit = {},
    onConfirmPasswordVisibilityToggle: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Same shell as Log in (07), but back-arrow header carrying the wordmark instead of an
            // inline one — this screen was navigated *to*, not landed on.
            ChefAITopAppBarWithWordmark(navigation = ChefAINavigation.Back(onClick = onNavigateBack))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("RegisterScreen")
                    .verticalScroll(scrollState)
                    .padding(RegisterContentPadding),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = stringResource(R.string.create_account),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(AuthMetrics.TitleToSubtitleGap))

                Text(
                    text = stringResource(R.string.create_account_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(AuthMetrics.SubtitleToFieldsGap))

                // Username Field
                UsernameTextField(
                    username = uiState.username,
                    onUsernameChange = onUsernameChange,
                    error = uiState.usernameError,
                    onNext = { focusManager.clearFocus() },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(AuthMetrics.FieldGap))

                // Email Field
                EmailTextField(
                    email = uiState.email,
                    onEmailChange = onEmailChange,
                    error = uiState.emailError,
                    onNext = { focusManager.clearFocus() },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(AuthMetrics.FieldGap))

                // Password Field
                PasswordTextField(
                    password = uiState.password,
                    onPasswordChange = onPasswordChange,
                    isVisible = uiState.isPasswordVisible,
                    onVisibilityToggle = onPasswordVisibilityToggle,
                    error = uiState.passwordError,
                    onNext = { focusManager.clearFocus() },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(AuthMetrics.FieldGap))

                // Confirm Password Field
                ConfirmPasswordTextField(
                    password = uiState.confirmPassword,
                    onPasswordChange = onConfirmPasswordChange,
                    isVisible = uiState.isConfirmPasswordVisible,
                    onVisibilityToggle = onConfirmPasswordVisibilityToggle,
                    error = uiState.confirmPasswordError,
                    onDone = onRegisterClick,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(AuthMetrics.FieldGap))

                // Register Button
                FlatBlockButton(
                    text = stringResource(R.string.register_button),
                    onClick = onRegisterClick,
                    loading = uiState.isLoading,
                    modifier = Modifier.testTag("RegisterButton"),
                )

                Spacer(modifier = Modifier.height(AuthMetrics.ButtonToFooterGap))

                // Login Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AuthMetrics.FooterItemGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.already_have_account),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = stringResource(R.string.login),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.chefColors.accentText,
                        modifier = Modifier.flatClickable(onClick = onLoginClick, role = Role.Button),
                    )
                }
            }
        }
    }
}

/** `padding: var(--space-6) var(--space-4) var(--space-4)` on the main content. */
private val RegisterContentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 16.dp)

@Composable
private fun UsernameTextField(
    username: String,
    onUsernameChange: (String) -> Unit,
    error: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlatField(
        value = username,
        onValueChange = onUsernameChange,
        label = stringResource(R.string.label_username),
        // FlatField's leadingIcon is an ImageVector, not the drawable-backed ChefAIIcons —
        // Material's icon here rather than the vendored Lucide user glyph. See PR notes.
        leadingIcon = Icons.Default.AccountCircle,
        errorText = error,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = modifier,
    )
}

@Composable
private fun EmailTextField(
    email: String,
    onEmailChange: (String) -> Unit,
    error: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlatField(
        value = email,
        onValueChange = onEmailChange,
        label = stringResource(R.string.label_email),
        placeholder = stringResource(R.string.placeholder_email),
        leadingIcon = Icons.Default.MailOutline,
        errorText = error,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = modifier,
    )
}

@Composable
private fun PasswordTextField(
    password: String,
    onPasswordChange: (String) -> Unit,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    error: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlatField(
        value = password,
        onValueChange = onPasswordChange,
        label = stringResource(R.string.label_password),
        leadingIcon = Icons.Default.Lock,
        trailing = { PasswordVisibilityToggle(isVisible = isVisible, onToggle = onVisibilityToggle) },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        errorText = error,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = modifier,
    )
}

@Composable
private fun ConfirmPasswordTextField(
    password: String,
    onPasswordChange: (String) -> Unit,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    error: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlatField(
        value = password,
        onValueChange = onPasswordChange,
        label = stringResource(R.string.label_confirm_password),
        leadingIcon = Icons.Default.Lock,
        trailing = { PasswordVisibilityToggle(isVisible = isVisible, onToggle = onVisibilityToggle) },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        errorText = error,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    ChefAITheme {
        RegisterScreenContent(
            uiState = RegisterUiState(
                username = "alice",
                email = "alice@example.com",
                password = "Password123!",
                confirmPassword = "Password123!"
            ),
            focusManager = LocalFocusManager.current,
            scrollState = rememberScrollState()
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RegisterScreenDarkPreview() {
    ChefAITheme(darkTheme = true) {
        RegisterScreenContent(
            uiState = RegisterUiState(
                username = "alice",
                email = "alice@example.com",
                password = "Password123!",
                confirmPassword = "Password123!"
            ),
            focusManager = LocalFocusManager.current,
            scrollState = rememberScrollState()
        )
    }
}
