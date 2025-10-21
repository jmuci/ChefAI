package com.tenmilelabs.chefai.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R

enum class InfoChipType {
    TAG,
    LABEL
}
@Composable
private fun chipColors(type: InfoChipType): Pair<Color, Color> {
    return when (type) {
        InfoChipType.TAG ->
            MaterialTheme.colorScheme.onTertiaryContainer to
                    MaterialTheme.colorScheme.tertiaryContainer

        InfoChipType.LABEL ->
            MaterialTheme.colorScheme.tertiaryContainer to
                    MaterialTheme.colorScheme.onTertiaryContainer
    }
}

@Composable
fun InfoChip(text: String, type: InfoChipType) {
    val (background, foreground) = chipColors(type)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = background,
        modifier = Modifier.padding(end = dimensionResource(id = R.dimen.padding_extra_small))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = foreground,
            modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.padding_small), vertical = dimensionResource(id = R.dimen.padding_extra_small))
        )
    }
}
