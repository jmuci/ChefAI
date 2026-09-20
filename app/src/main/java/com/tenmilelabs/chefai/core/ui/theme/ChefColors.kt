package com.tenmilelabs.chefai.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A nine-step tonal ramp, 100 (lightest) → 900 (darkest).
 *
 * The Modernist ramps are generated in OKLCH on one shared lightness scale, so the same step of
 * any ramp matches the others in visual value: `accent200` and `neutral200` are equally light.
 */
@Immutable
data class ChefRamp(
    val s100: Color,
    val s200: Color,
    val s300: Color,
    val s400: Color,
    val s500: Color,
    val s600: Color,
    val s700: Color,
    val s800: Color,
    val s900: Color,
) {
    /**
     * The ramp as an ordered list, 100 first — for fills cycled by index. The Search category
     * cards, for example, take `accent.steps[index % 3]`.
     */
    val steps: List<Color> = listOf(s100, s200, s300, s400, s500, s600, s700, s800, s900)
}

/**
 * The Modernist tokens that Material 3 has no slot for.
 *
 * Everything Material *does* have a slot for lives in `MaterialTheme.colorScheme` — see
 * [lightScheme] for the role→slot table. This holds the rest, so that no screen ever writes a
 * color literal or a magic rule width:
 *
 * - **[accent] / [neutral]** — the full ramps. Material exposes one `primary`, but the design
 *   cycles accent-100/200/300 as card fills and pairs accent-100 with accent-900 in the import
 *   error banner. Those steps have nowhere else to live.
 * - **[navSurface]** — the bottom-nav sage. Deliberately not the teal accent, and with no
 *   counterpart anywhere else in the system, so one named role covers it.
 * - **[sectionRuleWidth] / [rowRuleWidth] / [cornerRadius]** — the layout constants. This system
 *   organizes by rule weight rather than by shadow or fill, so the two widths are as much a token
 *   as any color is. They are theme-invariant: identical in light and dark by design, because a
 *   2dp rule is 2dp on any ground. Only the divider *color* (`colorScheme.outline`) changes.
 *
 * Read it through [chefColors]:
 * ```
 * val fill = MaterialTheme.chefColors.accent.s100
 * HorizontalDivider(thickness = MaterialTheme.chefColors.sectionRuleWidth)
 * ```
 */
@Immutable
data class ChefColors(
    val accent: ChefRamp,
    val neutral: ChefRamp,
    val navSurface: Color,
    val sectionRuleWidth: Dp,
    val rowRuleWidth: Dp,
    val cornerRadius: Dp,
)

/** The light tokens — the designed ones. */
internal val LightChefColors = ChefColors(
    accent = ChefRamp(
        s100 = ModernistPalette.Accent100,
        s200 = ModernistPalette.Accent200,
        s300 = ModernistPalette.Accent300,
        s400 = ModernistPalette.Accent400,
        s500 = ModernistPalette.Accent500,
        s600 = ModernistPalette.Accent600,
        s700 = ModernistPalette.Accent700,
        s800 = ModernistPalette.Accent800,
        s900 = ModernistPalette.Accent900,
    ),
    neutral = ChefRamp(
        s100 = ModernistPalette.Neutral100,
        s200 = ModernistPalette.Neutral200,
        s300 = ModernistPalette.Neutral300,
        s400 = ModernistPalette.Neutral400,
        s500 = ModernistPalette.Neutral500,
        s600 = ModernistPalette.Neutral600,
        s700 = ModernistPalette.Neutral700,
        s800 = ModernistPalette.Neutral800,
        s900 = ModernistPalette.Neutral900,
    ),
    navSurface = ModernistPalette.NavSage,
    sectionRuleWidth = ChefRuleWidths.Section,
    rowRuleWidth = ChefRuleWidths.Row,
    cornerRadius = ChefRuleWidths.CornerRadius,
)

/**
 * Deliberately unfinished. Magenta is not a Modernist color and never will be — anywhere it shows
 * up on screen, it is an un-designed dark token, not a styling bug.
 *
 * The handoff's instruction for dark is to leave what has not been designed *obviously* missing:
 * "a placeholder that looks plausible is harder to find and replace later than one that is
 * obviously unfinished". Grep `DarkUnset` to find every one of them.
 */
private val DarkUnset = Color(0xFFFF00FF)

/**
 * The dark tokens — **not designed**. See docs/design/modernist.md § Dark mode.
 *
 * TODO(dark): the accent ramp and the nav surface are the two things the handoff says will not
 *  invert mechanically, so they are left as [DarkUnset] rather than guessed:
 *
 *  - **The accent ramp.** In light, tinted fills come from the shallow end (accent-100/200/300)
 *    with accent-900 text. On a dark ground the fill has to come from the *deep* end with light
 *    text — the ramp direction flips, it does not just darken. Darkening each step one-for-one
 *    produces fills that vanish into the ground and text that fails contrast.
 *  - **The nav sage.** It has no dark counterpart at all. It is a lighter treatment chosen to set
 *    the nav bar apart from the ground, and what plays that role on a dark ground is an open
 *    question — possibly not a tint at all, possibly a rule.
 *
 *  The neutral ramp *is* filled in, mechanically inverted (100 ↔ 900), because a mono ramp does
 *  invert cleanly. It is still provisional: it has not been checked against the dark ground for
 *  contrast at the steps that carry text.
 */
internal val DarkChefColors = ChefColors(
    accent = ChefRamp(
        s100 = DarkUnset,
        s200 = DarkUnset,
        s300 = DarkUnset,
        s400 = DarkUnset,
        s500 = DarkUnset,
        s600 = DarkUnset,
        s700 = DarkUnset,
        s800 = DarkUnset,
        s900 = DarkUnset,
    ),
    // TODO(dark): provisional — mechanically inverted, contrast unverified.
    neutral = ChefRamp(
        s100 = ModernistPalette.Neutral900,
        s200 = ModernistPalette.Neutral800,
        s300 = ModernistPalette.Neutral700,
        s400 = ModernistPalette.Neutral600,
        s500 = ModernistPalette.Neutral500,
        s600 = ModernistPalette.Neutral400,
        s700 = ModernistPalette.Neutral300,
        s800 = ModernistPalette.Neutral200,
        s900 = ModernistPalette.Neutral100,
    ),
    navSurface = DarkUnset,
    // Theme-invariant: a rule is the same weight on any ground. Only its color changes.
    sectionRuleWidth = ChefRuleWidths.Section,
    rowRuleWidth = ChefRuleWidths.Row,
    cornerRadius = ChefRuleWidths.CornerRadius,
)

/**
 * The layout constants, in one place because they are theme-invariant and both themes need the
 * same values. Screens read them through [ChefColors], not from here.
 */
private object ChefRuleWidths {
    /** 2dp — above a group and below its last row. The heavy rule that separates sections. */
    val Section = 2.dp

    /** 1dp — between rows inside a group. */
    val Row = 1.dp

    /** 0dp, everywhere. The system has no rounded corners; the avatar circle is the one exception. */
    val CornerRadius = 0.dp
}

/**
 * The Modernist tokens for the current theme.
 *
 * Deliberately has no default: reading it outside [ChefAITheme] is a bug, and failing loudly at
 * that call site beats silently rendering the light ramp inside a dark screen.
 */
val LocalChefColors = staticCompositionLocalOf<ChefColors> {
    error("No ChefColors provided — wrap the composable (or its @Preview) in ChefAITheme { }.")
}

/**
 * The Modernist tokens, read the same way as `MaterialTheme.colorScheme`:
 * `MaterialTheme.chefColors.accent.s100`.
 */
val MaterialTheme.chefColors: ChefColors
    @Composable
    @ReadOnlyComposable
    get() = LocalChefColors.current
