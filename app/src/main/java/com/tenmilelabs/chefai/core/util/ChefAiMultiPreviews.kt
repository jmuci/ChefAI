package com.tenmilelabs.chefai.core.util

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Small font",
    group = "Font scales",
    fontScale = 0.5f
)
@Preview(
    name = "Large font",
    group = "Font scales",
    fontScale = 1.5f
)
annotation class FontScalePreviews

@Preview(
    "Dark Mode",
    "UI mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)

@Preview(
    "Light Mode",
    "UI mode",
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
)
annotation class DarkLightPreviews

