package com.tenmilelabs.chefai.developeroptions.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.MinHitTarget
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The underlined "Developer options" link pinned to the bottom of the login and Settings screens.
 *
 * Renders unconditionally: callers decide whether to show it (debug builds only), so previews and
 * tests can still see it.
 */
@Composable
fun DeveloperOptionsLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = MinHitTarget)
            .flatClickable(onClick = onClick, role = Role.Button)
            .testTag("DeveloperOptionsLink"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.developer_options_link),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.chefColors.accentText,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Preview(name = "Developer options link – light", showBackground = true)
@Composable
private fun DeveloperOptionsLinkPreview() {
    ChefAITheme {
        Surface { DeveloperOptionsLink(onClick = {}) }
    }
}

@Preview(
    name = "Developer options link – dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DeveloperOptionsLinkDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface { DeveloperOptionsLink(onClick = {}) }
    }
}
