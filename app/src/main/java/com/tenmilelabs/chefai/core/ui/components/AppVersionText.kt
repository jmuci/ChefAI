package com.tenmilelabs.chefai.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/** The app's release version, pinned to the bottom of the login and Settings screens. */
@Composable
fun AppVersionText(
    version: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.app_version_label, version),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag("AppVersionText"),
    )
}

@Preview(name = "App version – light", showBackground = true)
@Composable
private fun AppVersionTextPreview() {
    ChefAITheme {
        Surface { AppVersionText(version = "1.0-a1b2c3d-debug") }
    }
}

@Preview(
    name = "App version – dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppVersionTextDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface { AppVersionText(version = "1.0") }
    }
}
