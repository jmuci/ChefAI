package com.tenmilelabs.chefai.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/**
 * A section's opening title — a title-case heading over a muted subtitle, no rule. It opens a
 * panel (Home), where the panel's own edge already marks where the section starts.
 */
@Composable
fun SectionHeaderWithSubtitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(id = R.dimen.padding_medium)),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    top = dimensionResource(id = R.dimen.padding_extra_extra_small),
                ),
            )
        }
    }
}

@Preview(name = "SectionHeaderWithSubtitle — Light", showBackground = true)
@Composable
private fun SectionHeaderWithSubtitlePreview() {
    ChefAITheme {
        SectionHeaderWithSubtitle("Le title", "This is a subtitle, it's a bit longer")
    }
}

@Preview(
    name = "SectionHeaderWithSubtitle — Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SectionHeaderWithSubtitleDarkPreview() {
    ChefAITheme(darkTheme = true) {
        SectionHeaderWithSubtitle("Le title", "This is a subtitle, it's a bit longer")
    }
}
