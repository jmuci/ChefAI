package com.tenmilelabs.chefai.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * A section-opening label, matching the design's rule rhythm: the heavy 2dp rule that opens every
 * section in the design, then an uppercase kicker and a muted subtitle beneath.
 */
@Composable
fun SectionHeaderWithSubtitle(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = MaterialTheme.chefColors.sectionRuleWidth,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = dimensionResource(id = R.dimen.padding_medium),
                vertical = dimensionResource(id = R.dimen.padding_small),
            ),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = dimensionResource(id = R.dimen.padding_medium),
                vertical = dimensionResource(id = R.dimen.padding_extra_extra_small),
            ),
        )
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
