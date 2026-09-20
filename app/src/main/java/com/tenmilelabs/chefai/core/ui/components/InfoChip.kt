package com.tenmilelabs.chefai.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/** A recipe's category ([TAG], accent) vs. its dietary/other [LABEL] (neutral) — see `.tag` in styles.css. */
enum class InfoChipType {
    TAG,
    LABEL
}

@Composable
private fun chipColors(type: InfoChipType): Pair<Color, Color> {
    return when (type) {
        InfoChipType.TAG ->
            MaterialTheme.colorScheme.primaryContainer to
                    MaterialTheme.colorScheme.onPrimaryContainer

        InfoChipType.LABEL ->
            MaterialTheme.colorScheme.surfaceVariant to
                    MaterialTheme.colorScheme.onSurface
    }
}

/** A flat, zero-radius tag — the design's `.tag-accent` / `.tag-neutral`. */
@Composable
fun InfoChip(text: String, type: InfoChipType) {
    val (background, foreground) = chipColors(type)
    Surface(
        color = background,
        modifier = Modifier.padding(end = dimensionResource(id = R.dimen.padding_extra_small))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = foreground,
            modifier = Modifier.padding(
                horizontal = dimensionResource(id = R.dimen.padding_small),
                vertical = dimensionResource(id = R.dimen.padding_extra_small),
            )
        )
    }
}

@Preview(name = "InfoChip — Light")
@Composable
private fun InfoChipPreview() {
    ChefAITheme {
        Surface {
            InfoChip(text = "Seafood", type = InfoChipType.TAG)
        }
    }
}

@Preview(name = "InfoChip — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InfoChipDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface {
            InfoChip(text = "Asian", type = InfoChipType.LABEL)
        }
    }
}
