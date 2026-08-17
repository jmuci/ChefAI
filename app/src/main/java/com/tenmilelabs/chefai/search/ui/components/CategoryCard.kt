package com.tenmilelabs.chefai.search.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/** Background/foreground pairing for a [CategoryCard]; cycled across the grid so adjacent cards differ. */
enum class CategoryCardTone { PRIMARY, SECONDARY, TERTIARY }

/** A browse shortcut on the Search tab, rendered as a themed gradient card. */
@Composable
fun CategoryCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: CategoryCardTone = CategoryCardTone.PRIMARY,
) {
    val colorScheme = MaterialTheme.colorScheme
    val (startColor, endColor, contentColor) = when (tone) {
        CategoryCardTone.PRIMARY -> Triple(
            colorScheme.primaryContainer,
            colorScheme.secondaryContainer,
            colorScheme.onPrimaryContainer,
        )
        CategoryCardTone.SECONDARY -> Triple(
            colorScheme.secondaryContainer,
            colorScheme.tertiaryContainer,
            colorScheme.onSecondaryContainer,
        )
        CategoryCardTone.TERTIARY -> Triple(
            colorScheme.tertiaryContainer,
            colorScheme.primaryContainer,
            colorScheme.onTertiaryContainer,
        )
    }

    Card(
        onClick = onClick,
        modifier = modifier.testTag("CategoryCard"),
        shape = RoundedCornerShape(dimensionResource(R.dimen.card_corner_radius)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.category_card_height))
                .background(Brush.linearGradient(listOf(startColor, endColor))),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium)),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun CategoryCardLightPreview() {
    ChefAITheme {
        Surface {
            CategoryCard(title = "Breakfast", onClick = {})
        }
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun CategoryCardDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface {
            CategoryCard(title = "Breakfast", onClick = {})
        }
    }
}

@Preview(name = "Long title", showBackground = true)
@Composable
private fun CategoryCardLongTitlePreview() {
    ChefAITheme {
        Surface {
            CategoryCard(title = "Sandwiches & Wraps", onClick = {})
        }
    }
}

@Preview(name = "All tones", showBackground = true)
@Composable
private fun CategoryCardTonesPreview() {
    ChefAITheme {
        Surface {
            Row(
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small)),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            ) {
                CategoryCard(
                    title = "Breakfast",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    tone = CategoryCardTone.PRIMARY,
                )
                CategoryCard(
                    title = "Lunch",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    tone = CategoryCardTone.SECONDARY,
                )
                CategoryCard(
                    title = "Dinner",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    tone = CategoryCardTone.TERTIARY,
                )
            }
        }
    }
}
