package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The flat menu panel's geometry — screen 09 in the handoff, first drawn for the profile menu and
 * shared with the bookmark menu.
 */
object FlatMenuMetrics {
    /** `width: 228px`. Fixed, not wrap-content: the panel is a column of its own rhythm. */
    val PanelWidth = 228.dp

    /** `padding: var(--space-3)` along each row. */
    val Padding = 12.dp

    /** `min-height: 48px` per row — above the system's 44dp floor, as the design draws it. */
    val RowMinHeight = 48.dp

    /** `width/height: 18` on the Lucide glyph in a row; `gap: 12px` to its label. */
    val RowIconSize = 18.dp
    val RowIconGap = 12.dp

    /**
     * Breathing room around the panel inside the popup window, so `--shadow-lg` has somewhere to
     * fall.
     *
     * A `Popup` is its own window, sized to its content, and a shadow is drawn *outside* the
     * content bounds — with the panel flush against the window it is clipped away entirely and the
     * panel renders with a border and nothing else. `--shadow-lg` is `0 12px 32px`, so 24dp clears
     * the blur on every side. [BelowAnchorEndAligned] subtracts it again, so the panel itself still
     * lands exactly under its anchor.
     */
    val ShadowBleed = 24.dp
}

/**
 * Opens a [FlatMenuPanel] directly under the composable this is placed beside (put both in one
 * `Box`), right edges flush.
 *
 * Deliberately **not** `DropdownMenu`. Material's menu is a rounded, tonally-elevated `Surface`
 * with its own padding and item heights; this design's panel is a flat rectangle held by a 2dp ink
 * border, ruled internally, with a single drop shadow. Overriding Material's surface leaves its
 * insets and minimum item metrics behind, so the panel is a plain [Popup].
 */
@Composable
fun FlatMenuPopup(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        BelowAnchorEndAligned(with(density) { FlatMenuMetrics.ShadowBleed.roundToPx() })
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        FlatMenuPanel(content = content)
    }
}

/**
 * The panel itself: 228dp of ground, a 2dp ink border, `--shadow-lg`, zero radius.
 *
 * The border is `onBackground` — full-strength ink, not the divider. That is what makes this read
 * as a panel lifted off the page rather than as another ruled section of it, and it is the part
 * that has to survive into dark on its own: in dark
 * [chefColors][com.tenmilelabs.chefai.core.ui.theme.ChefColors.panelShadowElevation] is 0dp, since
 * the handoff is explicit that shadows read as noise on a dark ground.
 */
@Composable
fun FlatMenuPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(FlatMenuMetrics.ShadowBleed)
            .width(FlatMenuMetrics.PanelWidth)
            .shadow(
                elevation = MaterialTheme.chefColors.panelShadowElevation,
                shape = RectangleShape,
                // --shadow-lg is cast in #2d2b2b, the deepest neutral — not pure black.
                ambientColor = MaterialTheme.chefColors.neutral.s900,
                spotColor = MaterialTheme.chefColors.neutral.s900,
            )
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = MaterialTheme.chefColors.sectionRuleWidth,
                color = MaterialTheme.colorScheme.onBackground,
            ),
        content = content,
    )
}

/**
 * One row: 18dp glyph, 12dp gap, 14px label, 48dp minimum.
 *
 * The last row in a panel carries no rule beneath it — the panel's own border closes the column.
 */
@Composable
fun FlatMenuRow(
    label: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FlatMenuMetrics.RowMinHeight)
            // 45% on the whole row is the design's disabled treatment; it stands in for "inactive",
            // not for a paler color.
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .flatClickable(onClick = onClick, enabled = enabled, role = Role.Button)
            .padding(horizontal = FlatMenuMetrics.Padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlatMenuMetrics.RowIconGap),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(FlatMenuMetrics.RowIconSize),
            )
        }
        Text(
            text = label,
            // bodyLarge is the design's 14px body.
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Puts the panel directly under its anchor, right edges flush, then clamps it inside the window.
 *
 * `Popup`'s alignment-based providers position the panel *over* its anchor; the design anchors it
 * below. The clamp matters because anchors tend to sit at a trailing edge, so an un-clamped 228dp
 * panel would hang off-screen in RTL.
 */
private class BelowAnchorEndAligned(private val bleedPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // popupContentSize includes FlatMenuMetrics.ShadowBleed on every side; the panel's own
        // edges sit one bleed inside it, so every edge below is corrected by it.
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width + bleedPx
        } else {
            anchorBounds.left - bleedPx
        }
        return IntOffset(
            x = x.coerceIn(
                -bleedPx,
                (windowSize.width - popupContentSize.width + bleedPx).coerceAtLeast(-bleedPx),
            ),
            y = (anchorBounds.bottom - bleedPx).coerceIn(
                -bleedPx,
                (windowSize.height - popupContentSize.height + bleedPx).coerceAtLeast(-bleedPx),
            ),
        )
    }
}
