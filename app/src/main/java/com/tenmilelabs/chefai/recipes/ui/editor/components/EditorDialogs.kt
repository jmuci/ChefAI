package com.tenmilelabs.chefai.recipes.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
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
        ) {
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

private val DialogPadding = 24.dp
private val DialogMaxWidth = 360.dp
private val ContentPadding = 16.dp
private val ContentGap = 12.dp
