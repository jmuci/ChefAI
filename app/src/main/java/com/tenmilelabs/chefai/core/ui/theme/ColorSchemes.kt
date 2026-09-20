package com.tenmilelabs.chefai.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Modernist palette — the **only** place in the app where a color literal may be written.
 *
 * Values are lifted verbatim from the design handoff's `styles.css` `:root` block
 * (`~/Projects/design_handoff_chefai_redesign with_dark_mode/styles.css`). Anything derived from
 * them (muted ink, dividers) is computed once here and documented with the arithmetic, so a later
 * change to the ground or the ink can be re-derived rather than guessed.
 *
 * Screens never touch this object. They read `MaterialTheme.colorScheme` for the roles Material 3
 * has a slot for, and [LocalChefColors] for the ones it does not. See `docs/design/modernist.md`.
 */
internal object ModernistPalette {

    // ── Ground and ink ────────────────────────────────────────────────────────────────────────
    /** `--color-bg` — the one page ground. Every screen sits flush on it; there are no cards. */
    val Ground = Color(0xFFF3F2F2)

    /** `--color-surface` — the input/panel fill, one step down from the ground. */
    val GroundSunken = Color(0xFFEAE9E9)

    /** `--color-text` — full-strength ink. */
    val Ink = Color(0xFF201E1D)

    /**
     * Muted ink, **resolved to an opaque color** rather than carried as alpha on [Ink].
     *
     * `styles.css` writes this as `color-mix(in srgb, var(--color-text) 55%, transparent)`, i.e.
     * ink at 55% over whatever is behind it. Porting that as `Ink.copy(alpha = 0.55f)` is the one
     * mistake the handoff calls out by name: on a dark ground the same alpha collapses the
     * contrast instead of preserving it, because it mixes toward the ground, not away from it.
     *
     * So each theme resolves its own value. Light is ink at **65%** (the top of the handoff's
     * 55–65% band) composited over [Ground]:
     * `0.65 × 0x20 + 0.35 × 0xF3 = 0x6A` → `#6A6868`, which clears 4.5:1 against the ground
     * (4.85:1). The literal 55% mix the CSS specifies measures 3.66:1 and fails AA for the 11–13px
     * captions it is used on, so the band's darker end is the correct reading of the design.
     */
    val InkMuted = Color(0xFF6A6868)

    /**
     * `--color-divider` — ink at 40%, composited over [Ground] to an opaque value
     * (`0.40 × 0x20 + 0.60 × 0xF3 = 0x9F`). Opaque for the same reason as [InkMuted]: the rules
     * carry the whole layout in this system, and an alpha that reads correctly on the light ground
     * disappears on a dark one.
     */
    val Divider = Color(0xFF9F9D9D)

    /** The hairline variant — ink at 20% over [Ground]. Used for the 1dp row rules. */
    val DividerSoft = Color(0xFFC9C8C8)

    // ── Accent ramp (`--color-accent-*`) ──────────────────────────────────────────────────────
    val Accent100 = Color(0xFFE3F0EF)
    val Accent200 = Color(0xFFC4E1DE)
    val Accent300 = Color(0xFF96CAC5)
    val Accent400 = Color(0xFF57ABA4)
    val Accent500 = Color(0xFF128C87)

    /** `--color-accent` / `--color-accent-600` — the brand teal. */
    val Accent600 = Color(0xFF0A8080)
    val Accent700 = Color(0xFF0A6363)
    val Accent800 = Color(0xFF084A4A)
    val Accent900 = Color(0xFF053232)

    // ── Neutral ramp (`--color-neutral-*`) ────────────────────────────────────────────────────
    val Neutral100 = Color(0xFFF8F4F4)
    val Neutral200 = Color(0xFFEAE7E7)
    val Neutral300 = Color(0xFFD7D3D3)
    val Neutral400 = Color(0xFFBAB6B6)
    val Neutral500 = Color(0xFF9B9797)
    val Neutral600 = Color(0xFF7D7979)
    val Neutral700 = Color(0xFF605D5D)
    val Neutral800 = Color(0xFF444141)
    val Neutral900 = Color(0xFF2D2B2B)

    /**
     * The bottom-nav surface — a light sage, deliberately *not* the teal accent. It is the one
     * color in the system with no counterpart anywhere else, which is why it lives behind a single
     * named role ([ChefColors.navSurface]) that one edit can retheme.
     */
    val NavSage = Color(0xFFE7F0E0)

    // ── Dark — PROVISIONAL ────────────────────────────────────────────────────────────────────
    // TODO(dark): none of the values below were designed. The handoff ships a light palette only
    //  and says explicitly: build light exactly, keep dark compiling, do not invent a dark palette.
    //  These are a mechanical inversion of the mono ramp, good enough to render without crashing
    //  and nothing more. See docs/design/modernist.md § Dark mode before touching them.

    /** TODO(dark): provisional dark ground — ink stepped down, not a designed value. */
    val DarkGround = Color(0xFF1A1918)

    /** TODO(dark): provisional sunken dark ground. */
    val DarkGroundRaised = Color(0xFF242322)

    /**
     * TODO(dark): provisional muted ink for dark — the light ground at 65% over [DarkGround]
     * (7.23:1), derived the same way as [InkMuted] rather than by alpha, but on an undesigned
     * ground, so the number is only as trustworthy as the ground is.
     */
    val DarkInkMuted = Color(0xFFA7A6A6)

    /**
     * TODO(dark): provisional dark divider, ink-inverted at 40%. The handoff flags this value
     * specifically: the 2dp rules carry the layout, and in dark they must stay *as assertive as
     * they read in light*, which is not the same relative contrast. Expect to tune this by eye,
     * not to derive it.
     */
    val DarkDivider = Color(0xFF716F6F)

    /** TODO(dark): provisional hairline for dark. */
    val DarkDividerSoft = Color(0xFF3D3B3A)
}

/**
 * The light scheme — the real one. Every role below is the handoff's semantic role, mapped onto
 * the Material 3 slot that screens already reach for:
 *
 * | Handoff role            | M3 slot                    |
 * | ----------------------- | -------------------------- |
 * | Background `#f3f2f2`    | `background` / `surface`   |
 * | Ink `#201e1d`           | `onBackground` / `onSurface` |
 * | Muted ink               | `onSurfaceVariant`         |
 * | Accent `#0A8080`        | `primary`                  |
 * | Divider (40% ink)       | `outline`                  |
 * | Neutral-200 fill        | `surfaceVariant`           |
 *
 * The system is **mono**: `secondary` and `tertiary` are steps on the accent ramp, not new hues,
 * and `error` is `accent-700` — the handoff is explicit that destructive intent is carried by the
 * deep accent step because there is no error red in this palette.
 */
internal val lightScheme = lightColorScheme(
    primary = ModernistPalette.Accent600,
    onPrimary = ModernistPalette.Ground,
    primaryContainer = ModernistPalette.Accent100,
    onPrimaryContainer = ModernistPalette.Accent900,

    secondary = ModernistPalette.Accent700,
    onSecondary = ModernistPalette.Ground,
    secondaryContainer = ModernistPalette.Accent100,
    onSecondaryContainer = ModernistPalette.Accent800,

    tertiary = ModernistPalette.Accent800,
    onTertiary = ModernistPalette.Ground,
    tertiaryContainer = ModernistPalette.Accent200,
    onTertiaryContainer = ModernistPalette.Accent900,

    // Mono system: no error hue. The deep accent step carries destructive and invalid states.
    error = ModernistPalette.Accent700,
    onError = ModernistPalette.Ground,
    errorContainer = ModernistPalette.Accent100,
    onErrorContainer = ModernistPalette.Accent900,

    background = ModernistPalette.Ground,
    onBackground = ModernistPalette.Ink,
    surface = ModernistPalette.Ground,
    onSurface = ModernistPalette.Ink,
    surfaceVariant = ModernistPalette.Neutral200,
    onSurfaceVariant = ModernistPalette.InkMuted,

    outline = ModernistPalette.Divider,
    outlineVariant = ModernistPalette.DividerSoft,
    scrim = ModernistPalette.Neutral900,

    inverseSurface = ModernistPalette.Ink,
    inverseOnSurface = ModernistPalette.Ground,
    inversePrimary = ModernistPalette.Accent300,

    // Flat by rule: Material's tonal elevation overlay composites `surfaceTint` over the surface
    // as elevation rises (`surfaceTint.copy(alpha = f(elevation)).compositeOver(surface)`). Setting
    // the tint *to the surface color* makes that a no-op at every elevation, so an elevated
    // `Surface` stays exactly the ground and the 2dp rules remain the only depth cue.
    //
    // Not `Color.Transparent`: that is `Color(0, 0, 0, 0)`, and `.copy(alpha = …)` on it yields
    // **black** at that alpha. It dims every elevated surface instead of leaving it flat —
    // MainActivity's `Surface(tonalElevation = 5.dp)` rendered the ground at #DAD9D9 that way.
    surfaceTint = ModernistPalette.Ground,

    surfaceDim = ModernistPalette.Neutral300,
    surfaceBright = ModernistPalette.Neutral100,
    surfaceContainerLowest = ModernistPalette.Neutral100,
    surfaceContainerLow = ModernistPalette.Ground,
    surfaceContainer = ModernistPalette.GroundSunken,
    surfaceContainerHigh = ModernistPalette.Neutral200,
    surfaceContainerHighest = ModernistPalette.Neutral300,
)

/**
 * The dark scheme — **provisional**.
 *
 * TODO(dark): not designed. This exists so `ChefAITheme(darkTheme = true)` compiles and renders
 *  without crashing, which is all the handoff asks for at this stage. It is a mechanical inversion
 *  of the mono ramp; three things in it will *not* invert mechanically and need a design decision
 *  before anyone trusts them:
 *
 *  1. `primary` — the accent at full strength (`#0A8080`) is too dark to sit on a dark ground, so
 *     this uses `accent-400` and flips `onPrimary` from light to dark. That inverts the
 *     accent/ink relationship, which is a design call, not a derivation.
 *  2. `primaryContainer` / `errorContainer` — the light theme's tinted fills come from the *shallow*
 *     end of the ramp with deep text. In dark the fill must come from the deep end with light text.
 *     The ramp direction flips; it does not just darken.
 *  3. `outline` — see [ModernistPalette.DarkDivider].
 *
 *  See docs/design/modernist.md § Dark mode.
 */
internal val darkScheme = darkColorScheme(
    primary = ModernistPalette.Accent400,
    onPrimary = ModernistPalette.Accent900,
    primaryContainer = ModernistPalette.Accent800,
    onPrimaryContainer = ModernistPalette.Accent100,

    secondary = ModernistPalette.Accent300,
    onSecondary = ModernistPalette.Accent900,
    secondaryContainer = ModernistPalette.Accent800,
    onSecondaryContainer = ModernistPalette.Accent100,

    tertiary = ModernistPalette.Accent300,
    onTertiary = ModernistPalette.Accent900,
    tertiaryContainer = ModernistPalette.Accent800,
    onTertiaryContainer = ModernistPalette.Accent100,

    error = ModernistPalette.Accent300,
    onError = ModernistPalette.Accent900,
    errorContainer = ModernistPalette.Accent800,
    onErrorContainer = ModernistPalette.Accent100,

    background = ModernistPalette.DarkGround,
    onBackground = ModernistPalette.Ground,
    surface = ModernistPalette.DarkGround,
    onSurface = ModernistPalette.Ground,
    surfaceVariant = ModernistPalette.Neutral800,
    onSurfaceVariant = ModernistPalette.DarkInkMuted,

    outline = ModernistPalette.DarkDivider,
    outlineVariant = ModernistPalette.DarkDividerSoft,
    scrim = ModernistPalette.Neutral900,

    inverseSurface = ModernistPalette.Ground,
    inverseOnSurface = ModernistPalette.Ink,
    inversePrimary = ModernistPalette.Accent600,

    // See the light scheme: the tint is the surface, so tonal elevation is a no-op.
    surfaceTint = ModernistPalette.DarkGround,

    surfaceDim = ModernistPalette.DarkGround,
    surfaceBright = ModernistPalette.Neutral800,
    surfaceContainerLowest = Color(0xFF121110),
    surfaceContainerLow = ModernistPalette.DarkGround,
    surfaceContainer = ModernistPalette.DarkGroundRaised,
    surfaceContainerHigh = Color(0xFF2E2C2B),
    surfaceContainerHighest = ModernistPalette.Neutral800,
)
