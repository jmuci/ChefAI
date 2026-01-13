package com.tenmilelabs.chefai.auth.ui

import android.R.attr.enabled
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Handle UI events
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is LoginUiEvent.ShowSnackbar -> {
                    snackbarHostState?.showSnackbar(
                        message = context.getString(event.message),
                        duration = SnackbarDuration.Short
                    )
                }
                LoginUiEvent.NavigateToHome -> {
                    onNavigateToHome()
                }
                LoginUiEvent.NavigateToRegister -> {
                    onNavigateToRegister()
                }
            }
        }
    }

    LoginScreenContent(
        uiState = uiState,
        focusManager = focusManager,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onRememberMeChange = viewModel::onRememberMeChange,
        onPasswordVisibilityToggle = viewModel::onPasswordVisibilityToggle,
        onLoginClick = viewModel::onLoginClick,
        onCreateAccountClick = viewModel::onCreateAccountClick
    )
}

@Composable
fun LoginScreenContent(
    uiState: LoginUiState,
    focusManager: FocusManager,
    onEmailChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onRememberMeChange: (Boolean) -> Unit = {},
    onPasswordVisibilityToggle: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onCreateAccountClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("LoginScreen")
                .padding(dimensionResource(id = R.dimen.padding_large)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(
                    if (isSystemInDarkTheme()) {
                        R.drawable.ic_chef_hat_white_24dp
                    } else {
                        R.drawable.ic_chef_hat_black_24dp
                    }
                ),
                contentDescription = "ChefAI Logo",
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = stringResource(R.string.welcome_back),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email Field
            EmailTextField(
                email = uiState.email,
                onEmailChange = onEmailChange,
                error = uiState.emailError,
                onNext = { focusManager.clearFocus() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            PasswordTextField(
                password = uiState.password,
                onPasswordChange = onPasswordChange,
                isVisible = uiState.isPasswordVisible,
                onVisibilityToggle = onPasswordVisibilityToggle,
                error = uiState.passwordError,
                modifier = Modifier.fillMaxWidth(),
                onDone = onLoginClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Remember Me Checkbox
            RememberMeCheckbox(
                isChecked = uiState.rememberMe,
                onCheckedChange = onRememberMeChange
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Login Button
            Button(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("LoginButton")
                    .height(56.dp),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        stringResource(R.string.login_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Create Account Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dont_have_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(onClick = onCreateAccountClick,
                    modifier = Modifier.testTag("CreateAccountButton"),) {
                    Text(
                        stringResource(R.string.create_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmailTextField(
    email: String,
    onEmailChange: (String) -> Unit,
    error: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        modifier = modifier,
        label = { Text(stringResource(R.string.label_email)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null
            )
        },
        isError = error != null,
        supportingText = if (error != null) {
            { Text(text = error, color = MaterialTheme.colorScheme.error) }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() }
        ),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordTextField(
    password: String,
    onPasswordChange: (String) -> Unit,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = modifier,
        label = { Text(stringResource(R.string.label_password)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null
            )
        },
        trailingIcon = {
            IconButton(onClick = onVisibilityToggle) {
                Icon(
                    imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (isVisible) "Hide password" else "Show password"
                )
            }
        },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        isError = error != null,
        supportingText = if (error != null) {
            { Text(text = error, color = MaterialTheme.colorScheme.error) }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        singleLine = true
    )
}

@Composable
private fun RememberMeCheckbox(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(id = R.dimen.padding_extra_small)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
        
        Spacer(modifier = Modifier.size(8.dp))
        
        Text(
            stringResource(R.string.remember_me),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    ChefAITheme {
        LoginScreenContent(
            uiState = LoginUiState(
                email = "alice@example.com",
                password = "Password123!"
            ),
            focusManager = LocalFocusManager.current
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenDarkPreview() {
    ChefAITheme(darkTheme = true) {
        LoginScreenContent(
            uiState = LoginUiState(
                email = "alice@example.com",
                password = "Password123!"
            ),
            focusManager = LocalFocusManager.current
        )
    }
}