package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The flat text field — Modernist's `OutlinedTextField`.
 *
 * Three things make it not a Material field:
 * 1. **The label sits above the box**, in 12px muted, and stays there. There is no floating label
 *    that shrinks into a notch in the border; the border is an unbroken 2dp rule.
 * 2. **The border carries the state.** Rule color at rest, accent while focused, deep accent while
 *    invalid — which is why it does *not* also take the offset focus ring (`.input:focus-visible`
 *    sets `outline-offset: 0` precisely because the border already moved).
 * 3. **There is no error red.** The system is mono; invalid is `colorScheme.error`, which resolves
 *    to accent-700.
 *
 * ```
 * FlatField(
 *     value = uiState.email,
 *     onValueChange = { onAction(EmailChanged(it)) },
 *     label = stringResource(R.string.label_email),
 *     placeholder = "alice@example.com",
 *     leadingIcon = Icons.Default.MailOutline,
 *     errorText = uiState.emailError?.let { stringResource(it) },
 *     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
 * )
 * ```
 *
 * Used on log in (07), create account (08), accept invite (12), import (18) and the editor (19).
 *
 * @param errorText the message shown beneath the field. **This one parameter drives the whole
 *   error state** — the border, the message and the accessibility error announcement — so there is
 *   no `isError` flag that can disagree with it. `null` means valid.
 * @param minLines `1` (the default) is a true single-line field: no newlines, horizontal scroll.
 *   Anything greater makes it a multiline box that grows — the editor's "Description" is `3`.
 * @param trailing an optional slot at the right edge, for the password eye toggle or a clear
 *   button. It is inside the border and gets no padding of its own.
 */
@Composable
fun FlatField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val singleLine = minLines == 1

    val borderColor = when {
        errorText != null -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Column(modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(LabelGap))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (errorText != null) error(errorText) },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .heightIn(min = FieldMinHeight)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            width = MaterialTheme.chefColors.sectionRuleWidth,
                            color = borderColor,
                        )
                        .padding(horizontal = FieldHorizontalPadding, vertical = FieldVerticalPadding),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(IconGap),
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(LeadingIconSize),
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                    trailing?.invoke()
                }
            },
        )

        if (errorText != null) {
            Spacer(Modifier.size(LabelGap))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** `.field > label { margin-bottom: 5px }`, on the 4dp scale. */
private val LabelGap = 4.dp

/** The handoff's `min-height: 48dp` for the box itself. */
private val FieldMinHeight = 48.dp
private val FieldHorizontalPadding = 12.dp
private val FieldVerticalPadding = 12.dp

/** "leading icon 17dp Lucide stroke". */
private val LeadingIconSize = 17.dp
private val IconGap = 10.dp

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun FlatFieldPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Empty — placeholder showing")
        FlatField(
            value = "",
            onValueChange = {},
            label = "Email",
            placeholder = "alice@example.com",
            leadingIcon = Icons.Default.MailOutline,
        )
        PreviewStateLabel("Filled")
        FlatField(
            value = "alice@example.com",
            onValueChange = {},
            label = "Email",
            leadingIcon = Icons.Default.MailOutline,
        )
        PreviewStateLabel("Password — masked, with a trailing slot")
        FlatField(
            value = "hunter2hunter2",
            onValueChange = {},
            label = "Password",
            leadingIcon = Icons.Default.Lock,
            visualTransformation = PasswordVisualTransformation(),
            trailing = {
                Text(
                    text = "SHOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        PreviewStateLabel("Error")
        FlatField(
            value = "not-an-email",
            onValueChange = {},
            label = "Email",
            leadingIcon = Icons.Default.MailOutline,
            errorText = "Enter a valid email address",
        )
        PreviewStateLabel("Disabled")
        FlatField(
            value = "alice@example.com",
            onValueChange = {},
            label = "Email",
            enabled = false,
        )
        PreviewStateLabel("Multiline — minLines = 3")
        FlatField(
            value = "Salmon glazed with soy, mirin and brown sugar, served over steamed rice.",
            onValueChange = {},
            label = "Description *",
            minLines = 3,
        )
    }
}
