package com.tenmilelabs.chefai.core.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * Marks a bookmarked recipe as a go-to — one the meal planner should favour.
 *
 * Empty, it is the outline repeat glyph on the ground inside a ruled square; full, the square
 * fills with `primary` and the glyph turns `onPrimary`, so a glance tells the two apart the same
 * way [CookedToggleButton] does. The glyph is a placeholder while better imagery is explored —
 * swap [ChefAIIcons.Repeat] to change it everywhere.
 *
 * Only offered on a bookmarked recipe; callers hide it otherwise.
 *
 * @param size The square's side — 40dp on the details hero, smaller inline on a list row. The
 *   touch target never drops below the 48dp minimum either way.
 */
@Composable
fun GoToToggleButton(
    isGoTo: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val description = stringResource(
        if (isGoTo) R.string.go_to_unmark_content_description else R.string.go_to_mark_content_description
    )
    Box(
        modifier = modifier
            .testTag("GoToToggleButton")
            .minimumInteractiveComponentSize()
            .toggleable(value = isGoTo, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(if (isGoTo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                .border(
                    width = MaterialTheme.chefColors.sectionRuleWidth,
                    color = if (isGoTo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(ChefAIIcons.Repeat),
                contentDescription = null,
                tint = if (isGoTo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun GoToToggleButtonPreviewContent() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoToToggleButton(isGoTo = false, onToggle = {})
        GoToToggleButton(isGoTo = true, onToggle = {})
        GoToToggleButton(isGoTo = false, onToggle = {}, size = 32.dp)
        GoToToggleButton(isGoTo = true, onToggle = {}, size = 32.dp)
    }
}

@Preview(name = "Go-to toggle — Light", showBackground = true)
@Composable
private fun GoToToggleButtonLightPreview() {
    ChefAITheme(darkTheme = false) { GoToToggleButtonPreviewContent() }
}

@Preview(name = "Go-to toggle — Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun GoToToggleButtonDarkPreview() {
    ChefAITheme(darkTheme = true) { GoToToggleButtonPreviewContent() }
}
