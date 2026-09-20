package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/** The two tag fills in the design (`styles.css` `.tag-accent` / `.tag-neutral`). */
enum class FlatTagTone {
    /** Tinted accent — the meaningful one. Recipe tags, "Owner". */
    Accent,

    /** Tinted neutral — the incidental one. "Draft", a category that is not a claim. */
    Neutral,
}

/**
 * A small non-interactive label: a tinted block with text in the matching deep step.
 *
 * **It does not take an `onClick`, and that is the point.** If a tag needs to be tappable it is a
 * [FlatChip], which is a different size, a different weight and a different hit target. A tag that
 * quietly became a button is how a 44dp rule gets broken.
 *
 * ```
 * Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
 *     FlatTag("Japanese")
 *     FlatTag("Pescatarian", tone = FlatTagTone.Neutral)
 * }
 * ```
 *
 * Used on home (01), recipe detail (05), household (11) and the recipe editor (19).
 */
@Composable
fun FlatTag(
    text: String,
    modifier: Modifier = Modifier,
    tone: FlatTagTone = FlatTagTone.Accent,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = tone.contentColor(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(tone.containerColor())
            .padding(horizontal = TagHorizontalPadding, vertical = TagVerticalPadding),
    )
}

/**
 * The accent tag reads `primaryContainer` / `onPrimaryContainer` rather than
 * `chefColors.accent.s100` / `.s800`. In light those resolve to the same accent-100 fill the design
 * names (with accent-900 text, one step deeper than `.tag-accent`, which is the contrast-safer
 * read). In dark the ramp is `DarkUnset`, but the container roles have provisional values — so the
 * tag renders as a dark tinted block instead of magenta. See `docs/design/modernist.md` § Dark mode.
 */
@Composable
private fun FlatTagTone.containerColor(): Color = when (this) {
    FlatTagTone.Accent -> MaterialTheme.colorScheme.primaryContainer
    FlatTagTone.Neutral -> MaterialTheme.chefColors.neutral.s100
}

@Composable
private fun FlatTagTone.contentColor(): Color = when (this) {
    FlatTagTone.Accent -> MaterialTheme.colorScheme.onPrimaryContainer
    FlatTagTone.Neutral -> MaterialTheme.chefColors.neutral.s800
}

/** `.tag { padding: 3px 10px }`. */
private val TagHorizontalPadding = 10.dp
private val TagVerticalPadding = 3.dp

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun FlatTagPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Accent / neutral")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FlatTag(text = "Japanese")
            FlatTag(text = "Pescatarian", tone = FlatTagTone.Neutral)
        }
        PreviewStateLabel("Long label truncates rather than wrapping")
        FlatTag(text = "A tag whose label is much longer than the row can hold")
    }
}
