package com.tenmilelabs.chefai.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The Modernist theme: flat, architectural, ruled, zero-radius, Archivo throughout.
 *
 * Three things ship together and are meant to be read together:
 * - [lightScheme] / [darkScheme] — the handoff's semantic roles on Material 3 slots.
 * - [LocalChefColors] — the tokens Material has no slot for (the ramps, the nav sage, the rule
 *   widths). Read via `MaterialTheme.chefColors`.
 * - [ChefShapes] — 0dp on every size, so no call site needs to say so.
 *
 * Dark mode is **not designed**. It compiles and renders, and everything un-designed in it is
 * flagged `TODO(dark)` or rendered in magenta. See docs/design/modernist.md § Dark mode.
 */
@Composable
fun ChefAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkScheme else lightScheme
    val chefColors = if (darkTheme) DarkChefColors else LightChefColors

    CompositionLocalProvider(LocalChefColors provides chefColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = ChefShapes,
            content = content,
        )
    }
}
