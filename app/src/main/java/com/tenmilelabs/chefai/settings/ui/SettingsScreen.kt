package com.tenmilelabs.chefai.settings.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreview
import com.tenmilelabs.chefai.auth.domain.model.PublicRecipeDisposition
import com.tenmilelabs.chefai.auth.ui.PasswordVisibilityToggle
import com.tenmilelabs.chefai.core.domain.model.RecipeLanguage
import com.tenmilelabs.chefai.core.domain.model.WeekStart
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.ui.language.labelRes
import com.tenmilelabs.chefai.core.ui.components.AppVersionText
import com.tenmilelabs.chefai.core.ui.components.flat.DISABLED_ALPHA
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.components.flat.FlatRadio
import com.tenmilelabs.chefai.core.ui.components.flat.MinHitTarget
import com.tenmilelabs.chefai.core.ui.components.flat.RuledGroup
import com.tenmilelabs.chefai.core.ui.components.flat.flatSelectable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.util.appVersionLabel
import com.tenmilelabs.chefai.developeroptions.ui.DeveloperOptionsLink

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    /** `null` hides the Developer options link — the caller passes one on debug builds only. */
    onNavigateToDeveloperOptions: (() -> Unit)? = null,
    /** Called once the account is deleted and the device is back to an anonymous session. */
    onAccountDeleted: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnAccountDeleted by rememberUpdatedState(onAccountDeleted)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.AccountDeleted -> {
                    Toast.makeText(context, R.string.delete_account_done, Toast.LENGTH_LONG).show()
                    currentOnAccountDeleted()
                }
            }
        }
    }

    SettingsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        onDeveloperOptionsClick = onNavigateToDeveloperOptions,
        appVersion = appVersionLabel,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    /** `null` hides the Developer options section — the caller passes one on debug builds only. */
    onDeveloperOptionsClick: (() -> Unit)? = null,
    appVersion: String,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = SettingsMetrics.ScreenVerticalPadding),
    ) {
        SettingsSectionHeader(
            title = stringResource(R.string.settings_measurement_units_title),
            subtitle = stringResource(R.string.settings_measurement_units_subtitle),
        )

        RuledGroup(
            items = MeasurementSystemOption.entries,
            modifier = Modifier
                .padding(top = SettingsMetrics.SubtitleToGroupGap)
                .selectableGroup(),
        ) { option ->
            SettingsRadioRow(
                label = stringResource(option.labelRes),
                example = stringResource(option.exampleRes),
                isSelected = uiState.measurementSystem == option.system,
                onSelect = { onAction(SettingsAction.MeasurementSystemChanged(option.system)) },
            )
        }

        SettingsFootnote(stringResource(R.string.settings_measurement_units_footnote))

        Spacer(Modifier.height(SettingsMetrics.SectionGap))

        SettingsSectionHeader(
            title = stringResource(R.string.settings_recipe_language_title),
            subtitle = stringResource(R.string.settings_recipe_language_subtitle),
        )

        RuledGroup(
            items = RecipeLanguage.entries,
            modifier = Modifier
                .padding(top = SettingsMetrics.SubtitleToGroupGap)
                .selectableGroup(),
        ) { language ->
            SettingsRadioRow(
                label = stringResource(language.labelRes),
                example = null,
                isSelected = uiState.fallbackRecipeLanguage == language,
                onSelect = { onAction(SettingsAction.FallbackRecipeLanguageChanged(language)) },
            )
        }

        SettingsFootnote(stringResource(R.string.settings_recipe_language_footnote))

        Spacer(Modifier.height(SettingsMetrics.SectionGap))

        SettingsSectionHeader(
            title = stringResource(R.string.settings_week_start_title),
            subtitle = stringResource(R.string.settings_week_start_subtitle),
        )
        RuledGroup(
            items = WeekStartOption.entries,
            modifier = Modifier
                .padding(top = SettingsMetrics.SubtitleToGroupGap)
                .selectableGroup(),
        ) { option ->
            SettingsRadioRow(
                label = stringResource(option.labelRes),
                example = null,
                isSelected = uiState.weekStart == option.weekStart,
                onSelect = { onAction(SettingsAction.WeekStartChanged(option.weekStart)) },
            )
        }

        // Last of the user-facing sections; only Developer options (debug builds) sits below it.
        if (uiState.deleteAccount != DeleteAccountUiState.Unavailable) {
            DeleteAccountSection(state = uiState.deleteAccount, onAction = onAction)
        }

        if (onDeveloperOptionsClick != null) {
            DeveloperOptionsSection(onClick = onDeveloperOptionsClick)
        }

        // Last thing on the screen, under Developer options on debug builds.
        AppVersionText(
            version = appVersion,
            modifier = Modifier.padding(
                start = SettingsMetrics.HorizontalPadding,
                end = SettingsMetrics.HorizontalPadding,
                top = SettingsMetrics.SectionGap,
            ),
        )
    }
}

private enum class WeekStartOption(
    val weekStart: WeekStart,
    @param:StringRes val labelRes: Int,
) {
    MONDAY(WeekStart.MONDAY, R.string.settings_week_start_monday),
    SUNDAY(WeekStart.SUNDAY, R.string.settings_week_start_sunday),
}

/**
 * The destructive section: the same header as every other section, then a single destructive
 * block button — the same treatment as "Leave household". Tapping it fetches the deletion preview
 * (the button shows its loading state meanwhile) and opens [DeleteAccountDialog].
 */
@Composable
private fun DeleteAccountSection(
    state: DeleteAccountUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Spacer(Modifier.height(SettingsMetrics.SectionGap))
    SettingsSectionHeader(
        title = stringResource(R.string.settings_delete_account_title),
        subtitle = stringResource(R.string.settings_delete_account_subtitle),
    )
    FlatBlockButton(
        text = stringResource(R.string.settings_delete_account_button),
        onClick = { onAction(SettingsAction.DeleteAccountClicked) },
        variant = FlatButtonVariant.Destructive,
        loading = state == DeleteAccountUiState.LoadingPreview,
        modifier = Modifier.padding(
            start = SettingsMetrics.HorizontalPadding,
            end = SettingsMetrics.HorizontalPadding,
            top = SettingsMetrics.SubtitleToGroupGap,
        ),
    )
    if (state is DeleteAccountUiState.PreviewFailed) {
        Text(
            text = stringResource(state.error.messageRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(
                start = SettingsMetrics.HorizontalPadding,
                end = SettingsMetrics.HorizontalPadding,
                top = SettingsMetrics.TitleToSubtitleGap,
            ),
        )
    }
    if (state is DeleteAccountUiState.Confirming) {
        DeleteAccountDialog(state = state, onAction = onAction)
    }
}

/**
 * Confirmation, the public-recipe choice and the password re-entry, in one dialog — the same
 * shape as chefai-web's form. Built like the app's other destructive confirmations (leave
 * household, delete recipe): an [AlertDialog] whose confirm label takes the error role.
 */
@Composable
private fun DeleteAccountDialog(
    state: DeleteAccountUiState.Confirming,
    onAction: (SettingsAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(SettingsAction.DeleteAccountDismissed) },
        title = { Text(stringResource(R.string.delete_account_dialog_title)) },
        text = {
            DeleteAccountDialogBody(state = state, onAction = onAction)
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(SettingsAction.DeleteAccountConfirmed) },
                enabled = state.canSubmit,
            ) {
                Text(
                    text = stringResource(
                        if (state.isSubmitting) {
                            R.string.delete_account_confirm_button_loading
                        } else {
                            R.string.delete_account_confirm_button
                        },
                    ),
                    // An explicit color overrides TextButton's disabled tint, so dim it by hand
                    // while there's no password — but not mid-request, where "Deleting…" is
                    // the progress indicator.
                    color = MaterialTheme.colorScheme.error.copy(
                        alpha = if (state.canSubmit || state.isSubmitting) 1f else DISABLED_ALPHA,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(SettingsAction.DeleteAccountDismissed) },
                enabled = !state.isSubmitting,
            ) {
                Text(stringResource(R.string.cancel_button))
            }
        },
    )
}

@Composable
private fun DeleteAccountDialogBody(
    state: DeleteAccountUiState.Confirming,
    onAction: (SettingsAction) -> Unit,
) {
    val preview = state.preview
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SettingsMetrics.DialogParagraphGap),
    ) {
        DialogParagraph(
            if (preview.privateRecipeCount > 0) {
                pluralStringResource(
                    R.plurals.delete_account_private_recipes,
                    preview.privateRecipeCount,
                    preview.privateRecipeCount,
                )
            } else {
                stringResource(R.string.delete_account_private_recipes_none)
            },
        )
        DialogParagraph(stringResource(R.string.delete_account_personal_data))
        if (preview.ownsHousehold) {
            DialogParagraph(stringResource(R.string.delete_account_household))
        }
        DialogParagraph(stringResource(R.string.delete_account_signed_out))

        if (preview.requiresPublicRecipeChoice) {
            PublicRecipeChoice(
                publicRecipeCount = preview.publicRecipeCount,
                selected = state.publicRecipes,
                error = state.error?.takeIf { it.isAboutPublicRecipes },
                enabled = !state.isSubmitting,
                onAction = onAction,
            )
        }

        Text(
            text = stringResource(R.string.delete_account_irreversible),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
        )

        FlatField(
            value = state.password,
            onValueChange = { onAction(SettingsAction.DeleteAccountPasswordChanged(it)) },
            label = stringResource(R.string.delete_account_password_label),
            leadingIcon = ChefAIIcons.Lock,
            trailing = {
                PasswordVisibilityToggle(
                    isVisible = state.isPasswordVisible,
                    onToggle = { onAction(SettingsAction.DeleteAccountPasswordVisibilityToggled) },
                )
            },
            visualTransformation = if (state.isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            errorText = state.error
                ?.takeIf { it == DeleteAccountError.WRONG_PASSWORD }
                ?.let { stringResource(it.messageRes) },
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onAction(SettingsAction.DeleteAccountConfirmed) },
            ),
        )

        // Everything that isn't about one field: conflict, rate limit, expired session, other.
        state.error
            ?.takeIf { it != DeleteAccountError.WRONG_PASSWORD && !it.isAboutPublicRecipes }
            ?.let { error ->
                Text(
                    text = stringResource(error.messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
    }
}

/** The DELETE / ANONYMIZE radio pair. Nothing is selected until the user picks. */
@Composable
private fun PublicRecipeChoice(
    publicRecipeCount: Int,
    selected: PublicRecipeDisposition?,
    error: DeleteAccountError?,
    enabled: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    Column {
        Text(
            text = pluralStringResource(
                R.plurals.delete_account_public_recipes_question,
                publicRecipeCount,
                publicRecipeCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        RuledGroup(
            items = PublicRecipeOption.entries,
            modifier = Modifier
                .padding(top = SettingsMetrics.DialogParagraphGap)
                .selectableGroup(),
        ) { option ->
            SettingsRadioRow(
                label = stringResource(option.labelRes),
                example = null,
                isSelected = selected == option.disposition,
                onSelect = {
                    if (enabled) onAction(SettingsAction.PublicRecipesChoiceChanged(option.disposition))
                },
                horizontalPadding = 0.dp,
            )
        }
        if (error != null) {
            Text(
                text = stringResource(error.messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = SettingsMetrics.TitleToSubtitleGap),
            )
        }
    }
}

@Composable
private fun DialogParagraph(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

private enum class PublicRecipeOption(
    val disposition: PublicRecipeDisposition,
    @param:StringRes val labelRes: Int,
) {
    DELETE(PublicRecipeDisposition.DELETE, R.string.delete_account_public_recipes_delete),
    ANONYMIZE(PublicRecipeDisposition.ANONYMIZE, R.string.delete_account_public_recipes_anonymize),
}

private val DeleteAccountError.isAboutPublicRecipes: Boolean
    get() = this == DeleteAccountError.PUBLIC_RECIPE_CHOICE_MISSING ||
        this == DeleteAccountError.PUBLIC_RECIPES_CHANGED

@get:StringRes
private val DeleteAccountError.messageRes: Int
    get() = when (this) {
        DeleteAccountError.PUBLIC_RECIPE_CHOICE_MISSING -> R.string.delete_account_error_choice_missing
        DeleteAccountError.PUBLIC_RECIPES_CHANGED -> R.string.delete_account_error_public_recipes_changed
        DeleteAccountError.WRONG_PASSWORD -> R.string.delete_account_error_wrong_password
        DeleteAccountError.CONFLICT -> R.string.delete_account_error_conflict
        DeleteAccountError.RATE_LIMITED -> R.string.delete_account_error_rate_limited
        DeleteAccountError.SESSION_EXPIRED -> R.string.delete_account_error_session_expired
        DeleteAccountError.PREVIEW_FAILED -> R.string.settings_delete_account_preview_failed
        DeleteAccountError.GENERIC -> R.string.delete_account_error_generic
    }

/** Debug builds only — its own section, after the user-facing settings. */
@Composable
private fun DeveloperOptionsSection(onClick: () -> Unit) {
    Spacer(Modifier.height(SettingsMetrics.SectionGap))
    SettingsSectionHeader(
        title = stringResource(R.string.app_dest_title_developer_options),
        subtitle = stringResource(R.string.settings_developer_options_subtitle),
    )
    DeveloperOptionsLink(
        onClick = onClick,
        text = stringResource(R.string.settings_developer_options_open),
        modifier = Modifier.padding(horizontal = SettingsMetrics.HorizontalPadding),
    )
}

@Composable
private fun SettingsSectionHeader(title: String, subtitle: String) {
    Text(
        // labelMedium carries the uppercase tracking but not the transform — see Type.kt.
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = SettingsMetrics.HorizontalPadding),
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = SettingsMetrics.HorizontalPadding,
            vertical = SettingsMetrics.TitleToSubtitleGap,
        ),
    )
}

@Composable
private fun SettingsFootnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SettingsMetrics.HorizontalPadding),
    )
}

/**
 * One radio row — 44dp minimum, the selected label going 800. See [FlatRadio]'s own preview.
 * [example] is a second line under the label, omitted when the label says it all.
 */
@Composable
private fun SettingsRadioRow(
    label: String,
    example: String?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    /** `0.dp` inside a dialog, which brings its own side padding. */
    horizontalPadding: Dp = SettingsMetrics.HorizontalPadding,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinHitTarget)
            .flatSelectable(selected = isSelected, onClick = onSelect)
            .padding(
                horizontal = horizontalPadding,
                vertical = SettingsMetrics.RowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsMetrics.RadioGap),
    ) {
        FlatRadio(selected = isSelected, onClick = null)
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
            )
            if (example != null) {
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The section's own spacing, transcribed from the handoff's CSS margins (screen 10). */
private object SettingsMetrics {
    val ScreenVerticalPadding = 16.dp
    val HorizontalPadding = 16.dp

    /** `p { margin: 6px 0 var(--space-3) }` — 6px under the section label. */
    val TitleToSubtitleGap = 6.dp

    /** The subtitle's own `margin-bottom: var(--space-3)` before the ruled group. */
    val SubtitleToGroupGap = 12.dp

    /** `padding: var(--space-3) 0` inside each row. */
    val RowVerticalPadding = 12.dp
    val RadioGap = 12.dp

    /** Between one section's footnote and the next section's label. */
    val SectionGap = 32.dp

    /** Between the paragraphs of the delete-account dialog. */
    val DialogParagraphGap = 8.dp
}

/**
 * The three choices in the order they are offered. Each carries a worked example, because
 * "Metric" on its own does not tell anyone that a cup of flour will come back as grams while a cup
 * of stock comes back as millilitres.
 */
private enum class MeasurementSystemOption(
    val system: MeasurementSystem,
    @param:StringRes val labelRes: Int,
    @param:StringRes val exampleRes: Int,
) {
    AS_WRITTEN(
        MeasurementSystem.AS_WRITTEN,
        R.string.settings_units_as_written,
        R.string.settings_units_as_written_example,
    ),
    METRIC(
        MeasurementSystem.METRIC,
        R.string.settings_units_metric,
        R.string.settings_units_metric_example,
    ),
    IMPERIAL(
        MeasurementSystem.IMPERIAL,
        R.string.settings_units_imperial,
        R.string.settings_units_imperial_example,
    ),
}

@Preview(name = "Settings – light", showBackground = true)
@Composable
private fun SettingsContentPreview() {
    ChefAITheme {
        Surface {
            SettingsContent(
                uiState = SettingsUiState(
                    measurementSystem = MeasurementSystem.METRIC,
                    fallbackRecipeLanguage = RecipeLanguage.SPANISH,
                    deleteAccount = DeleteAccountUiState.Idle,
                ),
                onAction = {},
                onDeveloperOptionsClick = {},
                appVersion = "1.0-a1b2c3d-debug",
            )
        }
    }
}

@Preview(name = "Settings – dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsContentDarkPreview() {
    ChefAITheme {
        Surface {
            SettingsContent(
                uiState = SettingsUiState(
                    measurementSystem = MeasurementSystem.AS_WRITTEN,
                    deleteAccount = DeleteAccountUiState.PreviewFailed(DeleteAccountError.PREVIEW_FAILED),
                ),
                onAction = {},
                onDeveloperOptionsClick = {},
                appVersion = "1.0-a1b2c3d-debug",
            )
        }
    }
}

private val PreviewDeletion = DeleteAccountUiState.Confirming(
    preview = AccountDeletionPreview(publicRecipeCount = 3, privateRecipeCount = 12, ownsHousehold = true),
    publicRecipes = PublicRecipeDisposition.ANONYMIZE,
    password = "hunter2",
    error = DeleteAccountError.WRONG_PASSWORD,
)

@Preview(name = "Delete account dialog – light", showBackground = true)
@Composable
private fun DeleteAccountDialogPreview() {
    ChefAITheme {
        Surface {
            DeleteAccountDialogBody(state = PreviewDeletion, onAction = {})
        }
    }
}

@Preview(
    name = "Delete account dialog – dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DeleteAccountDialogDarkPreview() {
    ChefAITheme {
        Surface {
            DeleteAccountDialogBody(
                state = DeleteAccountUiState.Confirming(
                    preview = AccountDeletionPreview(publicRecipeCount = 1, privateRecipeCount = 0, ownsHousehold = false),
                    error = DeleteAccountError.PUBLIC_RECIPE_CHOICE_MISSING,
                ),
                onAction = {},
            )
        }
    }
}
