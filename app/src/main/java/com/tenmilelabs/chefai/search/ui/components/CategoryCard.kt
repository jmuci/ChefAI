package com.tenmilelabs.chefai.search.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/** Which step of the accent ramp a [CategoryCard] fills with; cycled across the grid by index. */
enum class CategoryCardTone { PRIMARY, SECONDARY, TERTIARY }

/** 76px min height, per the handoff's category-card spec. */
private val CardMinHeight = 76.dp

/**
 * A browse shortcut on the Search tab: a flat accent-ramp tile, label flush left and
 * bottom-aligned, with `chefColors.mediaRadius` corners — it stands in for card artwork, so it
 * rounds like an image does (docs/design/modernist.md § 1.4). No gradient.
 *
 * Fills cycle accent-100/200/300 with accent-900 text. The dark ramp is flipped, so the same steps
 * give deep fills with light text there — see `DarkChefColors`.
 */
@Composable
fun CategoryCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: CategoryCardTone = CategoryCardTone.PRIMARY,
) {
    val accent = MaterialTheme.chefColors.accent
    val fill: Color = when (tone) {
        CategoryCardTone.PRIMARY -> accent.s100
        CategoryCardTone.SECONDARY -> accent.s200
        CategoryCardTone.TERTIARY -> accent.s300
    }

    Box(
        modifier = modifier
            .testTag("CategoryCard")
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .clip(RoundedCornerShape(MaterialTheme.chefColors.mediaRadius))
            .background(fill)
            .flatClickable(onClick = onClick, role = Role.Button)
            .padding(CardPadding),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = accent.s900,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val CardPadding = 12.dp

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
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

@Preview(name = "All tones — dark", showBackground = true)
@Composable
private fun CategoryCardTonesDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
