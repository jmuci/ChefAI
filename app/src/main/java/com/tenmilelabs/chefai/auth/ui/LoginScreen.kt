package com.tenmilelabs.chefai.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
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
import com.tenmilelabs.chefai.core.ui.components.ChefAIWordmark
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatCheckbox
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.components.flat.MinHitTarget
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.components.flat.flatToggleable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    onNavigateToAcceptInvite: (String) -> Unit = {}
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
                is LoginUiEvent.NavigateToAcceptInvite -> {
                    onNavigateToAcceptInvite(event.token)
                }
            }
        }
    }

    LoginScreenContent(
        uiState = uiState,
        focusManager = focusManager,
        onEmailChange = viewModel::onEmailChange,
        onEmailSuggestionSelected = viewModel::onEmailSuggestionSelected,
        onEmailSuggestionsDismissed = viewModel::onEmailSuggestionsDismissed,
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
    onEmailSuggestionSelected: (EmailSuggestion) -> Unit = {},
    onEmailSuggestionsDismissed: () -> Unit = {},
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
        // Flush left, vertically centered — Modernist forbids centered hero copy.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("LoginScreen")
                .padding(
                    horizontal = dimensionResource(id = R.dimen.padding_medium),
                    vertical = dimensionResource(id = R.dimen.padding_large),
                ),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            ChefAIWordmark()

            Spacer(modifier = Modifier.height(AuthMetrics.WordmarkToTitleGap))

            Text(
                text = stringResource(R.string.welcome_back),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(AuthMetrics.TitleToSubtitleGap))

            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(AuthMetrics.SubtitleToFieldsGap))

            // Email Field with autocomplete dropdown
            EmailTextField(
                email = uiState.email,
                suggestions = uiState.emailSuggestions,
                onEmailChange = onEmailChange,
                onSuggestionSelected = onEmailSuggestionSelected,
                onDismissSuggestions = onEmailSuggestionsDismissed,
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
                modifier = Modifier.fillMaxWidth(),
                onDone = onLoginClick
            )

            Spacer(modifier = Modifier.height(AuthMetrics.FieldGap))

            // Remember Me row
            RememberMeRow(
                isChecked = uiState.rememberMe,
                onCheckedChange = onRememberMeChange
            )

            Spacer(modifier = Modifier.height(AuthMetrics.FieldGap))

            // Login Button
            FlatBlockButton(
                text = stringResource(R.string.login_button),
                onClick = onLoginClick,
                loading = uiState.isLoading,
                modifier = Modifier.testTag("LoginButton"),
            )

            Spacer(modifier = Modifier.height(AuthMetrics.ButtonToFooterGap))

            // Create Account Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AuthMetrics.FooterItemGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dont_have_account),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.create_account),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.chefColors.accentText,
                    modifier = Modifier
                        .flatClickable(onClick = onCreateAccountClick, role = Role.Button)
                        .testTag("CreateAccountButton"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailTextField(
    email: String,
    suggestions: List<EmailSuggestion>,
    onEmailChange: (String) -> Unit,
    onSuggestionSelected: (EmailSuggestion) -> Unit,
    onDismissSuggestions: () -> Unit,
    error: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { if (!it) onDismissSuggestions() },
        modifier = modifier
    ) {
        FlatField(
            value = email,
            onValueChange = {
                onEmailChange(it)
                expanded = true
            },
            label = stringResource(R.string.label_email),
            placeholder = stringResource(R.string.placeholder_email),
            leadingIcon = ChefAIIcons.Mail,
            errorText = error,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    expanded = false
                    onNext()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = {
                expanded = false
                onDismissSuggestions()
            }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion.email) },
                    onClick = {
                        expanded = false
                        onSuggestionSelected(suggestion)
                    }
                )
            }
        }
    }
}

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
    FlatField(
        value = password,
        onValueChange = onPasswordChange,
        label = stringResource(R.string.label_password),
        leadingIcon = ChefAIIcons.Lock,
        trailing = { PasswordVisibilityToggle(isVisible = isVisible, onToggle = onVisibilityToggle) },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        errorText = error,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        modifier = modifier,
    )
}

/**
 * The eye / eye-off trailing toggle shared by every password field in the auth flow (07, 08).
 * 17dp glyph, matching the field's own leading-icon size; the clickable bounds are left at the
 * icon's size since [FlatField]'s trailing slot sits inside the 48dp-tall border already.
 */
@Composable
internal fun PasswordVisibilityToggle(isVisible: Boolean, onToggle: () -> Unit) {
    Icon(
        painter = painterResource(if (isVisible) ChefAIIcons.Eye else ChefAIIcons.EyeOff),
        contentDescription = stringResource(
            if (isVisible) R.string.content_description_hide_password else R.string.content_description_show_password,
        ),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(FieldIconSize)
            .flatClickable(onClick = onToggle, role = Role.Button),
    )
}

private val FieldIconSize = 17.dp

@Composable
private fun RememberMeRow(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinHitTarget)
            .flatToggleable(checked = isChecked, onCheckedChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuthMetrics.RememberMeGap),
    ) {
        FlatCheckbox(checked = isChecked, onCheckedChange = null)
        Text(
            stringResource(R.string.remember_me),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * The auth shell's vertical rhythm, transcribed from the handoff's CSS margins (screens 07/08 in
 * `ChefAI Redesign.dc.html`). CSS collapses adjacent margins to their max; these are already that
 * resolved value, not a straight copy of each element's own margin.
 */
internal object AuthMetrics {
    /** `h2 { margin: var(--space-3) 0 6px }` — the wordmark's own margin is 0, so this wins. */
    val WordmarkToTitleGap = 12.dp

    /** `h2`'s bottom margin. */
    val TitleToSubtitleGap = 6.dp

    /** The subtitle's `margin: 0 0 var(--space-6)`. */
    val SubtitleToFieldsGap = 24.dp

    /** `.field { margin-bottom: var(--space-3) }`. */
    val FieldGap = 12.dp

    /** `margin-top: var(--space-4)` on the footer row. */
    val ButtonToFooterGap = 16.dp

    /** `gap: 6px` between the footer's prompt and its link. */
    val FooterItemGap = 6.dp

    /** `label { gap: 10px }` on the remember-me row. */
    val RememberMeGap = 10.dp
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
