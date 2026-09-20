package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The button treatments in the design (`styles.css` `.btn-primary` / `.btn-secondary` /
 * `.btn-ghost`), plus the destructive reading of the secondary one.
 *
 * Note what is *not* here: a red. The system is mono, and destructive intent is carried by the
 * deep accent step (`colorScheme.error`, which resolves to accent-700) — that is [Destructive].
 */
enum class FlatButtonVariant {
    /** Accent fill, ground-colored label. One per screen; it is the screen's commitment. */
    Primary,

    /** Transparent with a 2dp rule around it. The "and also" action next to a [Primary]. */
    Secondary,

    /** No fill, no border, accent label. A link that happens to be a button. */
    Ghost,

    /**
     * [Secondary]'s shape in the error role — deep-accent label *and* border. "Leave household",
     * "Delete recipe". Reach for this rather than tinting a [Secondary] by hand.
     */
    Destructive,
}

/**
 * The full-width block button — `.btn-block`, 52dp tall, **label flush left**.
 *
 * This is the form used for every screen's committing action ("Log In", "Create Plan", "Import",
 * "Save recipe", "Join household"). The flush-left label is not an accident of the markup: nothing
 * in Modernist is centered, and a centered button label is the single most common way a screen
 * ends up looking off-system.
 *
 * ```
 * FlatBlockButton(
 *     text = stringResource(R.string.wizard_create_plan),
 *     onClick = { onAction(CreatePlan) },
 *     loading = uiState.isSaving,
 * )
 * ```
 *
 * @param loading swaps the label for a progress indicator and stops accepting clicks. The button
 *   keeps its size, so the footer does not jump. Use it for the `isLoading` / `isSaving` states
 *   rather than disabling the button and showing a spinner elsewhere.
 * @param enabled `false` renders the whole button at 45% opacity, per `.btn:disabled`.
 * @param leadingIcon drawn 18dp before the label, tinted to match it. Decorative — the label
 *   carries the meaning, so it gets no content description.
 */
@Composable
fun FlatBlockButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FlatButtonVariant = FlatButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    FlatButtonBody(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(BlockHeight),
        variant = variant,
        enabled = enabled,
        loading = loading,
        leadingIcon = leadingIcon,
        horizontalPadding = BlockHorizontalPadding,
        horizontalArrangement = Arrangement.Start,
    )
}

/**
 * The inline button — `.btn`, wrapping its content, label centered, 44dp minimum hit target.
 *
 * Use it where two or more actions sit side by side in a row ("Remove", "Revoke", "By email") or
 * where the action is smaller than the screen. When the action *is* the screen, use
 * [FlatBlockButton].
 */
@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FlatButtonVariant = FlatButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    FlatButtonBody(
        text = text,
        onClick = onClick,
        modifier = modifier.heightIn(min = MinHitTarget),
        variant = variant,
        enabled = enabled,
        loading = loading,
        leadingIcon = leadingIcon,
        horizontalPadding = if (variant == FlatButtonVariant.Ghost) {
            GhostHorizontalPadding
        } else {
            InlineHorizontalPadding
        },
        horizontalArrangement = Arrangement.Center,
    )
}

@Composable
private fun FlatButtonBody(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    variant: FlatButtonVariant,
    enabled: Boolean,
    loading: Boolean,
    leadingIcon: ImageVector?,
    horizontalPadding: Dp,
    horizontalArrangement: Arrangement.Horizontal,
) {
    val contentColor = variant.contentColor()
    val clickable = enabled && !loading

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            // Fill *before* the click modifier so the pressed tint replaces it; border *after* it
            // so the tint does not paint over the 2dp rule that defines the button.
            .then(variant.fillModifier())
            .flatClickable(
                onClick = onClick,
                enabled = clickable,
                role = Role.Button,
                pressedTint = variant.pressedTint(),
            )
            .then(variant.borderModifier())
            .semantics { if (loading) contentDescription = text }
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(IconSize),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(IconSize),
                )
                Spacer(Modifier.size(IconGap))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── variant → roles ───────────────────────────────────────────────────────────────────────────

@Composable
private fun FlatButtonVariant.contentColor(): Color = when (this) {
    FlatButtonVariant.Primary -> MaterialTheme.colorScheme.onPrimary
    FlatButtonVariant.Secondary -> MaterialTheme.colorScheme.onBackground
    FlatButtonVariant.Ghost -> MaterialTheme.colorScheme.primary
    FlatButtonVariant.Destructive -> MaterialTheme.colorScheme.error
}

/** The resting fill. Goes *before* the click modifier: the pressed tint is meant to replace it. */
@Composable
private fun FlatButtonVariant.fillModifier(): Modifier = when (this) {
    FlatButtonVariant.Primary -> Modifier.background(MaterialTheme.colorScheme.primary)
    FlatButtonVariant.Secondary,
    FlatButtonVariant.Ghost,
    FlatButtonVariant.Destructive,
    -> Modifier
}

/**
 * The 2dp rule. Goes *after* the click modifier, because the pressed tint fills the whole bounds
 * and would otherwise paint straight over it — the button would lose its outline mid-press.
 */
@Composable
private fun FlatButtonVariant.borderModifier(): Modifier = when (this) {
    FlatButtonVariant.Secondary -> Modifier.border(
        width = MaterialTheme.chefColors.sectionRuleWidth,
        color = MaterialTheme.colorScheme.outline,
    )
    FlatButtonVariant.Destructive -> Modifier.border(
        width = MaterialTheme.chefColors.sectionRuleWidth,
        color = MaterialTheme.colorScheme.error,
    )
    FlatButtonVariant.Primary, FlatButtonVariant.Ghost -> Modifier
}

/**
 * The pressed fill — "one step along the accent ramp", per the handoff.
 *
 * Two of the three deliberately read a Material role rather than the `chefColors` ramp step the
 * light design names, because **the accent ramp is `DarkUnset` (magenta) in dark mode** and a
 * button that flashes magenta on every tap is noise, not the honest signal that an un-designed
 * *resting* color is. The roles chosen resolve to the specified light value anyway:
 *
 * - [Primary] → `secondary`, which **is** accent-700 in the light scheme (`.btn-primary:active`).
 *   Dark resolves it to accent-300, a step *lighter*, which is the right direction on a dark ground.
 * - [Ghost] → `primaryContainer`, accent-100 in light. The design's pressed step is accent-200,
 *   one deeper; this is the hover step, and the difference is a whisper on a text button.
 * - [Secondary] → `chefColors.neutral.s300`, exactly as specified. The neutral ramp *is* filled in
 *   for dark, so there is nothing to work around.
 */
@Composable
private fun FlatButtonVariant.pressedTint(): Color = when (this) {
    FlatButtonVariant.Primary -> MaterialTheme.colorScheme.secondary
    FlatButtonVariant.Secondary -> MaterialTheme.chefColors.neutral.s300
    FlatButtonVariant.Ghost -> MaterialTheme.colorScheme.primaryContainer
    FlatButtonVariant.Destructive -> MaterialTheme.chefColors.neutral.s300
}

/** `.btn-block` — the 52dp committing action. */
private val BlockHeight = 52.dp
private val BlockHorizontalPadding = 16.dp

/** `.btn` — `padding: var(--space-2) calc(var(--space-3) * 1.2)`, rounded to the 14dp the chips use. */
private val InlineHorizontalPadding = 14.dp

/** `.btn-ghost { padding-inline: var(--space-1) }` — a link sits tight to its text. */
private val GhostHorizontalPadding = 4.dp

private val IconSize = 18.dp
private val IconGap = 8.dp

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun FlatBlockButtonPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Primary")
        FlatBlockButton(text = "Create Plan", onClick = {})
        PreviewStateLabel("Primary — leading icon")
        FlatBlockButton(
            text = "Shopping list · 24 items",
            onClick = {},
            leadingIcon = Icons.Default.ShoppingCart,
        )
        PreviewStateLabel("Primary — loading")
        FlatBlockButton(text = "Create Plan", onClick = {}, loading = true)
        PreviewStateLabel("Primary — disabled")
        FlatBlockButton(text = "Import", onClick = {}, enabled = false)
        PreviewStateLabel("Secondary")
        FlatBlockButton(
            text = "Invite via link",
            onClick = {},
            variant = FlatButtonVariant.Secondary,
            leadingIcon = Icons.Default.Link,
        )
        PreviewStateLabel("Ghost")
        FlatBlockButton(
            text = "Enter it manually",
            onClick = {},
            variant = FlatButtonVariant.Ghost,
        )
    }
}

@LightDarkPreview
@Composable
private fun FlatInlineButtonPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Inline — primary / secondary / ghost")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatButton(text = "Next", onClick = {})
            FlatButton(text = "Back", onClick = {}, variant = FlatButtonVariant.Secondary)
            FlatButton(text = "Revoke", onClick = {}, variant = FlatButtonVariant.Ghost)
        }
        PreviewStateLabel("Inline — disabled / loading")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatButton(text = "Next", onClick = {}, enabled = false)
            FlatButton(text = "Saving", onClick = {}, loading = true)
        }
        PreviewStateLabel("Destructive — the deep accent step, not a red")
        FlatBlockButton(
            text = "Leave household",
            onClick = {},
            variant = FlatButtonVariant.Destructive,
        )
    }
}
