package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The two interaction primitives every Modernist control is built from.
 *
 * The design system has no ripple. `styles.css` gives interactive surfaces a **flat tint one step
 * along a ramp** while pressed, and a **2dp accent ring drawn 2dp outside the bounds** while
 * focused. Material's `Indication` does neither — its ripple is bounded by the component's shape
 * and its focus highlight is an inside-the-bounds overlay — so both are hand-rolled here and every
 * component in this package routes through them.
 *
 * See `docs/design/modernist.md` § Interaction states.
 */

/** `outline: 2px solid var(--color-accent)`. */
private val FocusRingWidth = 2.dp

/** `outline-offset: 2px` — the ring sits *outside* the bounds, not on them. */
private val FocusRingOffset = 2.dp

/**
 * Draws the system focus ring around this composable whenever [interactionSource] reports focus.
 *
 * Use this overload; it reads both the focus state and the accent from the theme, so there is
 * nothing to get wrong:
 *
 * ```
 * val interactionSource = remember { MutableInteractionSource() }
 * Box(Modifier.modernistFocusRing(interactionSource).clickable(interactionSource, null) { … })
 * ```
 *
 * The ring is drawn **outside** the content bounds, so leave 2–4dp of breathing room around
 * focusable elements in a tight grid: a `Modifier.clip` or a scroll container upstream will crop
 * it. Nothing in this package clips.
 */
@Composable
fun Modifier.modernistFocusRing(interactionSource: InteractionSource): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return modernistFocusRing(focused = focused, color = MaterialTheme.colorScheme.primary)
}

/**
 * The explicit-state overload, for callers that already track focus themselves (a text field that
 * also wants the ring while its error is showing, for instance). Prefer the [InteractionSource]
 * overload — it cannot be passed the wrong color.
 */
fun Modifier.modernistFocusRing(
    focused: Boolean,
    color: Color,
    width: Dp = FocusRingWidth,
    offset: Dp = FocusRingOffset,
): Modifier = drawWithContent {
    drawContent()
    if (!focused) return@drawWithContent
    val stroke = width.toPx()
    val gap = offset.toPx()
    // drawRect strokes centred on the path, so half the stroke sits inside the rect it is given:
    // push the rect out by half a stroke as well as by the offset to land the ring's inner edge
    // exactly `offset` away from the bounds.
    val outset = gap + stroke / 2f
    drawRect(
        color = color,
        topLeft = Offset(-outset, -outset),
        size = Size(size.width + 2f * outset, size.height + 2f * outset),
        style = Stroke(width = stroke),
    )
}

/**
 * Makes this composable clickable the Modernist way: **no ripple**, a flat [pressedTint] while
 * held, and the focus ring while focused.
 *
 * This is the list-row primitive — every tappable row in the design (the profile menu, meal-plan
 * days, a recipe card) is this modifier plus a `heightIn(min = 44.dp)`:
 *
 * ```
 * Row(
 *     modifier = Modifier
 *         .fillMaxWidth()
 *         .heightIn(min = 44.dp)
 *         .flatClickable(onClick = onClick, role = Role.Button)
 *         .padding(horizontal = 16.dp),
 * ) { … }
 * ```
 *
 * Use it only for rows that *do* something. A row that toggles or selects needs the state in its
 * semantics, so it wants [flatToggleable] or [flatSelectable] instead — `role` alone tells a
 * screen reader what kind of control it is but never what it is currently set to.
 *
 * **Order matters: apply any `border` _after_ this modifier.** The pressed tint fills the bounds,
 * so a border applied before it is painted over and vanishes for the duration of the press.
 *
 * The default tint is the row tint from the design's interaction table (`neutral-200` pressed).
 * Buttons pass their own — see [FlatButton].
 *
 * @param pressedTint the fill painted behind the content while pressed. Rest state paints nothing,
 *   so the ground shows through.
 */
@Composable
fun Modifier.flatClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = null,
    pressedTint: Color = MaterialTheme.chefColors.neutral.s200,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier = flatInteraction(pressedTint, enabled, interactionSource)
    .clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        role = role,
        onClick = onClick,
    )

/**
 * [flatClickable] for a row or control that **toggles**: identical visuals, but the checked state
 * reaches accessibility services.
 *
 * `clickable(role = Role.Checkbox)` publishes the *kind* of control and nothing else, so TalkBack
 * announces a checked and an unchecked chip identically. `toggleable` publishes the state, which
 * in this design matters more than usual — selection is carried by fill and font weight, and a
 * screen-reader user has no other signal at all.
 *
 * **Order matters: apply any `border` _after_ this modifier.** See [flatClickable].
 */
@Composable
fun Modifier.flatToggleable(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Checkbox,
    pressedTint: Color = MaterialTheme.chefColors.neutral.s200,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier = flatInteraction(pressedTint, enabled, interactionSource)
    .toggleable(
        value = checked,
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        role = role,
        onValueChange = onCheckedChange,
    )

/**
 * [flatClickable] for a row or control that is **one option of several** — a Settings radio row, a
 * single-select chip group, a segment. Publishes the selected state, for the reason in
 * [flatToggleable].
 *
 * The enclosing `Column`/`Row` should carry `Modifier.selectableGroup()` so the set is announced
 * as one.
 *
 * **Order matters: apply any `border` _after_ this modifier.** See [flatClickable].
 */
@Composable
fun Modifier.flatSelectable(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
    pressedTint: Color = MaterialTheme.chefColors.neutral.s200,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier = flatInteraction(pressedTint, enabled, interactionSource)
    .selectable(
        selected = selected,
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        role = role,
        onClick = onClick,
    )

/** The half the three share: the pressed tint and the focus ring, in that draw order. */
@Composable
private fun Modifier.flatInteraction(
    pressedTint: Color,
    enabled: Boolean,
    interactionSource: InteractionSource,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    return this
        .then(if (pressed && enabled) Modifier.background(pressedTint) else Modifier)
        .modernistFocusRing(interactionSource)
}

/** `.btn:disabled { opacity: 0.45 }` — the one place alpha is the right tool in this system. */
internal const val DISABLED_ALPHA = 0.45f

/** The design's minimum hit target. Chips, switch rows, list rows and menu items are all this. */
internal val MinHitTarget = 44.dp
