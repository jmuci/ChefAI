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
 * - **[accentText]** — the accent step that is legible as small text. `colorScheme.primary` is
 *   accent-600, which measures 4.26:1 on the ground and fails AA below large-text sizes. Accent
 *   *text* is a different role from accent *fill*, so it gets its own token rather than a note in
 *   a doc that a call site can miss.
 * - **[panelGround] / [panelSurface] / [panelRadius] / [mediaRadius]** — the surface model a
 *   screen opts into instead of rules: a slightly sunken page with lighter, rounded panels on it,
 *   separated by color and space rather than lines. Home uses it; the ruled screens do not yet.
 *   See docs/design/modernist.md § 3.1.
 * - **[navSurface] / [navIndicator] / [navActiveContent] / [navShade]** — the bottom nav: a
 *   panel-toned bar with a soft shade cast upward instead of a rule, and a tinted pill behind the
 *   active tab.
 * - **[sectionRuleWidth] / [rowRuleWidth] / [cornerRadius]** — the layout constants. This system
 *   organizes by rule weight rather than by shadow or fill, so the two widths are as much a token
 *   as any color is. They are theme-invariant: identical in light and dark by design, because a
 *   2dp rule is 2dp on any ground. Only the divider *color* (`colorScheme.outline`) changes.
 * - **[panelShadowElevation]** — the one place this system casts a shadow, and the one dark value
 *   the handoff actually *decides* rather than defers. See the property.
 *
 * Read it through [chefColors]:
 * ```
 * val fill = MaterialTheme.chefColors.accent.s100
 * Text(label, color = MaterialTheme.chefColors.accentText)
 * HorizontalDivider(thickness = MaterialTheme.chefColors.sectionRuleWidth)
 * ```
 */
@Immutable
data class ChefColors(
    val accent: ChefRamp,
    val neutral: ChefRamp,
    /**
     * Accent used as **text or an icon on the ground** — section labels, links, the current-day
     * highlight, the accent kicker.
     *
     * Light resolves to accent-700 (6.31:1), not the brand accent-600 (4.26:1, below the 4.5:1
     * AA floor for anything under large-text size). Nearly every accent string in this design is
     * 11–14px, so the safe step is the default and the brand step is the exception.
     *
     * Use `colorScheme.primary` for accent **fills** (button grounds, the wizard progress bar,
     * number badges, selected chips) where ground-colored text sits on top of it.
     */
    val accentText: Color,
    /** The page *behind* panels — one step darker than a panel so the panels lift by tone. */
    val panelGround: Color,
    /** A panel's fill — the lighter surface a section sits on. */
    val panelSurface: Color,
    /** The bottom nav's (and rail's) fill. */
    val navSurface: Color,
    /** The pill behind the active nav tab's icon. */
    val navIndicator: Color,
    /** The active nav tab's icon and label — AA-safe on [navIndicator] and [navSurface]. */
    val navActiveContent: Color,
    /**
     * The shade the nav casts upward onto the content beneath it, in place of a rule.
     * [Color.Transparent] in dark, where shadows read as noise and tone does the work.
     */
    val navShade: Color,
    val sectionRuleWidth: Dp,
    val rowRuleWidth: Dp,
    val cornerRadius: Dp,
    /** A panel's corner radius. Containers only — controls (buttons, inputs, chips) stay square. */
    val panelRadius: Dp,
    /** A recipe image's corner radius, one step tighter than [panelRadius] so it nests inside one. */
    val mediaRadius: Dp,
    val panelShadowElevation: Dp,
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
    accentText = ModernistPalette.Accent700,
    panelGround = ModernistPalette.GroundSunken,
    panelSurface = ModernistPalette.Neutral100,
    navSurface = ModernistPalette.Neutral100,
    navIndicator = ModernistPalette.Accent100,
    navActiveContent = ModernistPalette.Accent700,
    navShade = ModernistPalette.NavShade,
    sectionRuleWidth = ChefRuleWidths.Section,
    rowRuleWidth = ChefRuleWidths.Row,
    cornerRadius = ChefRuleWidths.CornerRadius,
    panelRadius = ChefRuleWidths.PanelRadius,
    mediaRadius = ChefRuleWidths.MediaRadius,
    panelShadowElevation = ChefRuleWidths.PanelShadow,
)

/**
 * The placeholder for a dark token nobody has decided yet. Magenta is not a Modernist color and
 * never will be — anywhere it shows up on screen, it is an un-designed dark token, not a styling
 * bug. A plausible-looking guess is harder to find and replace later than an obviously unfinished
 * one. No token uses it today; a new `ChefColors` field without a dark answer should
 * (docs/design/modernist.md § Adding a token).
 */
@Suppress("unused")
private val DarkUnset = Color(0xFFFF00FF)

/**
 * The dark tokens — **derived, not designed**. See docs/design/modernist.md § Dark mode.
 *
 * - **The accent ramp is flipped**, 100 ↔ 900, not darkened. In light, tinted fills come from the
 *   shallow end (accent-100/200/300) with accent-900 text; on a dark ground the fill has to come
 *   from the *deep* end with light text. Flipping the steps keeps every call site's pairing right
 *   in both themes — `accent.s100` stays "the subtle tinted fill" and `accent.s900` "the text on
 *   it" — and keeps the accent ramp step-matched to the neutral ramp, which flips the same way.
 *   Measured: the light-text-on-fill pairs clear 6:1 (s300) to 11.9:1 (s100), and the fills sit
 *   1.1–2.2:1 off a dark panel, the same separation light's tiles have off a light one.
 * - **[ChefColors.accentText]** follows the flip to accent-300 (8.6:1 on a dark panel, 9.7:1 on
 *   the ground) — the same color as the active nav tab.
 * - The panel and nav surfaces are derived as well: a deep page with raised panels on it, so they
 *   separate by tone, and no nav shade.
 * - The neutral ramp is mechanically inverted (100 ↔ 900) — a mono ramp inverts cleanly. It has
 *   not been contrast-checked at the steps that carry text.
 */
internal val DarkChefColors = ChefColors(
    // Flipped, like the neutral ramp below: a deep fill with light text on it. See the KDoc.
    accent = ChefRamp(
        s100 = ModernistPalette.Accent900,
        s200 = ModernistPalette.Accent800,
        s300 = ModernistPalette.Accent700,
        s400 = ModernistPalette.Accent600,
        s500 = ModernistPalette.Accent500,
        s600 = ModernistPalette.Accent400,
        s700 = ModernistPalette.Accent300,
        s800 = ModernistPalette.Accent200,
        s900 = ModernistPalette.Accent100,
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
    // accent.s700 in both themes; the flip lands it on accent-300 here.
    accentText = ModernistPalette.Accent300,
    // TODO(dark): derived, not designed — the panels lift off a deeper page by tone alone.
    panelGround = ModernistPalette.DarkGroundDeep,
    panelSurface = ModernistPalette.DarkGroundRaised,
    navSurface = ModernistPalette.DarkGroundRaised,
    // The ramp flips on a dark ground: a deep fill with a shallow accent on it.
    navIndicator = ModernistPalette.Accent800,
    navActiveContent = ModernistPalette.Accent300,
    navShade = Color.Transparent,
    // Theme-invariant: a rule is the same weight on any ground. Only its color changes.
    sectionRuleWidth = ChefRuleWidths.Section,
    rowRuleWidth = ChefRuleWidths.Row,
    cornerRadius = ChefRuleWidths.CornerRadius,
    panelRadius = ChefRuleWidths.PanelRadius,
    mediaRadius = ChefRuleWidths.MediaRadius,
    // Not a TODO(dark) — the handoff decides this one: "shadows in particular read as noise on a
    // dark surface; the profile-menu panel (screen 09) uses --shadow-lg plus a 2px border, and in
    // dark the border should do the work". Zero, deliberately.
    panelShadowElevation = 0.dp,
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

    /**
     * 0dp — controls (buttons, inputs, chips) and everything Material shapes from the theme. Only
     * the containers below round; the avatar circle stays the one other exception.
     */
    val CornerRadius = 0.dp

    /** 12dp — a panel's corners. */
    val PanelRadius = 12.dp

    /** 8dp — a recipe image's corners: [PanelRadius] minus a step, so it nests inside a panel. */
    val MediaRadius = 8.dp

    /**
     * 12dp — the light value for `--shadow-lg` (`0 12px 32px #2d2b2b/22%`), the only shadow the
     * design uses: the profile-menu panel floating over dimmed content. Everything else in this
     * system is flush on the ground and separated by rules.
     *
     * Dark is **not** a TODO: the handoff decides it explicitly at 0dp — see [DarkChefColors].
     */
    val PanelShadow = 12.dp
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
