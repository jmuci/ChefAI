package com.tenmilelabs.chefai.recipes.ui.editor.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipeLanguage
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.components.flat.FlatRadio
import com.tenmilelabs.chefai.core.ui.components.flat.MinHitTarget
import com.tenmilelabs.chefai.core.ui.components.flat.flatSelectable
import com.tenmilelabs.chefai.core.ui.language.labelRes
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The unsaved-changes prompt — not drawn in the handoff for screen 19; the instruction is to use
 * the profile-menu panel treatment (screen 09) here too. [Dialog] rather than Material's
 * `AlertDialog`, whose rounded, tonally-elevated `Card` is exactly the surface this system doesn't
 * have; the platform default width is turned off so the panel can be sized like every other panel
 * in the system instead of Material's fixed alert width.
 */
@Composable
fun UnsavedChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    Dialog(
        onDismissRequest = onKeepEditing,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogPanel {
            Text(
                text = stringResource(R.string.unsaved_changes_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.unsaved_changes_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                FlatButton(
                    text = stringResource(R.string.keep_editing_button),
                    onClick = onKeepEditing,
                    variant = FlatButtonVariant.Ghost,
                )
                FlatButton(
                    text = stringResource(R.string.discard_button),
                    onClick = onDiscard,
                    variant = FlatButtonVariant.Destructive,
                )
            }
        }
    }
}

/**
 * Asks which language an imported recipe is written in, when the page didn't make it clear. One
 * radio row per offered language, [selectedCode] pre-ticked; same panel treatment as
 * [UnsavedChangesDialog]. Dismissing ("Not now", or tapping outside) keeps the guess unconfirmed.
 */
@Composable
fun LanguagePromptDialog(
    selectedCode: String,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LanguagePromptContent(
            selectedCode = selectedCode,
            onSelect = onSelect,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/** [LanguagePromptDialog]'s panel without the window, so it can be previewed. */
@Composable
private fun LanguagePromptContent(
    selectedCode: String,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogPanel {
        Text(
            text = stringResource(R.string.language_prompt_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.language_prompt_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.selectableGroup()) {
            RecipeLanguage.entries.forEach { language ->
                val isSelected = language.code == selectedCode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinHitTarget)
                        .flatSelectable(selected = isSelected, onClick = { onSelect(language.code) })
                        .testTag("LanguagePromptOption_${language.code}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RadioGap),
                ) {
                    FlatRadio(selected = isSelected, onClick = null)
                    Text(
                        text = stringResource(language.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            FlatButton(
                text = stringResource(R.string.language_prompt_not_now),
                onClick = onDismiss,
                variant = FlatButtonVariant.Ghost,
            )
            FlatButton(
                text = stringResource(R.string.language_prompt_confirm),
                onClick = onConfirm,
                modifier = Modifier.testTag("LanguagePromptConfirm"),
            )
        }
    }
}

/** The bordered, shadowed panel every editor dialog sits in — the profile-menu treatment (screen 09). */
@Composable
private fun DialogPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(DialogPadding)
            .widthIn(max = DialogMaxWidth)
            .shadow(
                elevation = MaterialTheme.chefColors.panelShadowElevation,
                shape = RectangleShape,
                ambientColor = MaterialTheme.chefColors.neutral.s900,
                spotColor = MaterialTheme.chefColors.neutral.s900,
            )
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = MaterialTheme.chefColors.sectionRuleWidth,
                color = MaterialTheme.colorScheme.onBackground,
            )
            .padding(ContentPadding),
        verticalArrangement = Arrangement.spacedBy(ContentGap),
        content = content,
    )
}

@Preview(name = "Language prompt – light", showBackground = true)
@Composable
private fun LanguagePromptPreview() {
    ChefAITheme {
        Surface {
            LanguagePromptContent(selectedCode = "es", onSelect = {}, onConfirm = {}, onDismiss = {})
        }
    }
}

@Preview(name = "Language prompt – dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LanguagePromptDarkPreview() {
    ChefAITheme {
        Surface {
            LanguagePromptContent(selectedCode = "en", onSelect = {}, onConfirm = {}, onDismiss = {})
        }
    }
}

private val DialogPadding = 24.dp
private val DialogMaxWidth = 360.dp
private val ContentPadding = 16.dp
private val ContentGap = 12.dp
private val RadioGap = 12.dp
