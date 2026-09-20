package com.tenmilelabs.chefai.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tenmilelabs.chefai.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val archivo = GoogleFont("Archivo")

/**
 * Archivo, in the three weights the design system ships (`styles.css` imports 400/600/800).
 *
 * One family for everything: `--font-heading` and `--font-body` are both Archivo, so headings and
 * body differ by weight and tracking, never by typeface. [displayFontFamily] and [bodyFontFamily]
 * are kept as separate names only so the split stays available if that ever changes; today they
 * are the same family and should be treated as one.
 */
val archivoFontFamily = FontFamily(
    Font(googleFont = archivo, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = archivo, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = archivo, fontProvider = provider, weight = FontWeight.ExtraBold),
)

val displayFontFamily = archivoFontFamily
val bodyFontFamily = archivoFontFamily

/**
 * `--font-heading-weight: 800`. Headings, button labels, section labels and any emphasized value
 * are all ExtraBold in this system; there is no bold/semibold middle ground in the design.
 */
private val Heavy = FontWeight.ExtraBold

/** Headings: `line-height: 1.12`, `letter-spacing: -0.015em` (`styles.css` h1–h6). */
private val HeadingTracking = (-0.015).em

/** Uppercase section labels: `letter-spacing` 0.08–0.1em. 0.08em for 11–13px, 0.1em at 10px. */
private val LabelTracking = 0.08.em
private val LabelTrackingWide = 0.1.em

/**
 * The Modernist type scale mapped onto Material 3's thirteen slots.
 *
 * The handoff's scale is CSS — six heading steps plus body and label sizes. Material has thirteen
 * named slots and every Compose component reaches for one of them, so each slot gets a real value:
 * a slot left on the Material default is a slot that renders at the wrong size the first time a
 * component picks it up.
 *
 * | Slot              | Size / weight              | Design role                                    |
 * | ----------------- | -------------------------- | ---------------------------------------------- |
 * | `displayLarge`    | 42 / 800                   | h1 — not used on any screen in this batch       |
 * | `displayMedium`   | 32 / 800                   | h2 — recipe title on detail, invite household   |
 * | `displaySmall`    | 25 / 800                   | h3 — "Tonight" recipe title, household name     |
 * | `headlineLarge`   | 20 / 800                   | h4 — screen titles ("Settings", "Recipes")      |
 * | `headlineMedium`  | 18 / 800                   | the wordmark, invite code                       |
 * | `headlineSmall`   | 16 / 800                   | h5 — grid card titles                           |
 * | `titleLarge`      | 17 / 800                   | `.card-title` — list card titles                |
 * | `titleMedium`     | 15 / 800                   | profile display name, editor title value        |
 * | `titleSmall`      | 13 / 800, UPPER, .08em     | h6 — section headers ("BY MEAL")                |
 * | `bodyLarge`       | 14 / 400                   | body, list row titles when not emphasized       |
 * | `bodyMedium`      | 13 / 400                   | secondary body, meta lines, quantities          |
 * | `bodySmall`       | 12 / 400                   | muted captions, examples, footnotes (1.55 lh)   |
 * | `labelLarge`      | 14 / 800                   | `.btn` label                                    |
 * | `labelMedium`     | 11 / 800, UPPER, .08em     | section labels, kickers, "14 RESULTS"           |
 * | `labelSmall`      | 10 / 800, UPPER, .1em      | nav labels, "SIGNED IN AS", card kickers        |
 *
 * Two rules that are not expressible in a `TextStyle` and must be applied at the call site:
 * **uppercase is not automatic** — `titleSmall`, `labelMedium` and `labelSmall` carry the tracking
 * of an uppercase label but not the transform, so pass already-uppercased text (or
 * `String.uppercase()`); and **color is never baked in** — muted text is
 * `colorScheme.onSurfaceVariant`, not a lighter style.
 */
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 42.sp,
        lineHeight = 47.sp,
        letterSpacing = HeadingTracking,
    ),
    displayMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = HeadingTracking,
    ),
    displaySmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 25.sp,
        lineHeight = 28.sp,
        letterSpacing = HeadingTracking,
    ),
    headlineLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 20.sp,
        lineHeight = 22.sp,
        letterSpacing = HeadingTracking,
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 18.sp,
        lineHeight = 20.sp,
        letterSpacing = HeadingTracking,
    ),
    headlineSmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 16.sp,
        lineHeight = 18.sp,
        letterSpacing = HeadingTracking,
    ),
    titleLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 17.sp,
        lineHeight = 20.sp,
        letterSpacing = HeadingTracking,
    ),
    titleMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = HeadingTracking,
    ),
    // h6 — a section header. Uppercase at the call site.
    titleSmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 13.sp,
        lineHeight = 15.sp,
        letterSpacing = LabelTracking,
    ),
    bodyLarge = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
    ),
    // Uppercase at the call site.
    labelMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = LabelTracking,
    ),
    // Uppercase at the call site.
    labelSmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = Heavy,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = LabelTrackingWide,
    ),
)
