package com.tenmilelabs.chefai.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

@Composable
fun SectionHeaderWithSubtitle(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .height(dimensionResource(id = R.dimen.section_header_height))
            .fillMaxWidth(),
        contentAlignment = Alignment.TopStart
    ) {
        // Section title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    horizontal = dimensionResource(id = R.dimen.padding_medium),
                    vertical = dimensionResource(id = R.dimen.padding_small)

                )
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    horizontal = 16.dp,
                    vertical = dimensionResource(id = R.dimen.padding_extra_extra_small)
                )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderWithSubtitlePreview() {
    ChefAITheme {
        SectionHeaderWithSubtitle("Le title", "This is a subtitle, it's a bit longer")
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderWithSubtitleDarkPreview() {
    ChefAITheme(darkTheme = true) {
        SectionHeaderWithSubtitle("Le title", "This is a subtitle, it's a bit longer")
    }
}
