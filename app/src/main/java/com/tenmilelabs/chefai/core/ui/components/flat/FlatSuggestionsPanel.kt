package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The suggestions list under a text field — the ingredient, tag and label autocompletes on the
 * recipe editor (screen 19). Not drawn in the handoff; the instruction is to use the profile-menu
 * panel treatment (screen 09) for it, so this reads the same [ChefColors.panelShadowElevation] +
 * ink border + [SectionRule]/[RowRule] rhythm as [com.tenmilelabs.chefai.auth.ui.UserProfileMenu]'s
 * panel, rather than `ExposedDropdownMenu`'s rounded, tonally-elevated `Surface`.
 *
 * Deliberately **inline**, not a `Popup`: a floating panel needs to match the anchoring field's
 * width, which means measuring it and re-deriving `UserProfileMenu`'s shadow-bleed math for an
 * anchor that (unlike the avatar) resizes with the keyboard and the field's own error state. Placed
 * directly under the field in the normal layout flow, the panel gets the field's width for free by
 * sharing its parent, at the cost of pushing whatever comes after it down the screen while open —
 * an acceptable trade for a list that is a handful of rows, not menu-length.
 *
 * @param suggestions empty renders nothing, including the border and shadow.
 */
@Composable
fun FlatSuggestionsPanel(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = MaterialTheme.chefColors.panelShadowElevation,
                shape = RectangleShape,
                ambientColor = MaterialTheme.chefColors.neutral.s900,
                spotColor = MaterialTheme.chefColors.neutral.s900,
            )
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = MaterialTheme.chefColors.sectionRuleWidth,
                color = MaterialTheme.colorScheme.onBackground,
            ),
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) RowRule()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinHitTarget)
                    .flatClickable(onClick = { onSuggestionClick(suggestion) }, role = Role.Button)
                    .padding(horizontal = SuggestionHorizontalPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val SuggestionHorizontalPadding = 12.dp

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun FlatSuggestionsPanelPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Suggestions")
        FlatSuggestionsPanel(
            suggestions = listOf("Basmati rice", "Bomba rice", "Brown rice"),
            onSuggestionClick = {},
        )
    }
}
