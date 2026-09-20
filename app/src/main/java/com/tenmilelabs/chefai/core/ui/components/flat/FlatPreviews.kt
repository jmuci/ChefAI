package com.tenmilelabs.chefai.core.ui.components.flat

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/**
 * Preview plumbing shared by this package, so that every component can show every state in both
 * themes without twelve lines of boilerplate per state.
 *
 * Dark is **not designed** (see `docs/design/modernist.md` § Dark-mode discipline). The dark
 * previews exist to prove each component still renders and to make the un-designed tokens visible,
 * not because dark is finished. Anything magenta in them is an unset token by design.
 */

/**
 * Renders the annotated preview twice: once light, once dark.
 *
 * The composable it annotates should use plain `ChefAITheme { }` — the theme's default
 * `isSystemInDarkTheme()` picks up the preview's `uiMode`, so one preview body covers both.
 */
@Preview(name = "Light", group = "flat", showBackground = true)
@Preview(
    name = "Dark",
    group = "flat",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
internal annotation class LightDarkPreview

/**
 * The preview ground: the themed background, 16dp of padding and 12dp between states. Using a
 * themed `Surface` rather than `@Preview(backgroundColor = …)` keeps the preview honest — a
 * component that forgets to state its own color shows up wrong here instead of looking fine on a
 * hard-coded white card.
 */
@Composable
internal fun FlatPreviewSurface(content: @Composable ColumnScope.() -> Unit) {
    ChefAITheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** A 10px uppercase caption naming the state beneath it, so a preview sheet reads as a table. */
@Composable
internal fun PreviewStateLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
