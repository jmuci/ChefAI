package com.tenmilelabs.chefai.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.ui.HomeAction

@Composable
fun SduiCarousel(
    carousel: ComponentModel.Carousel,
    recipes: Map<String, RecipePreview>,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = carousel.items,
                key = { it.id },
            ) { item ->
                ComponentRenderer(
                    component = item,
                    recipes = recipes,
                    onAction = onAction,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// -- Previews -----------------------------------------------------------------

private val sampleCarousel = ComponentModel.Carousel(
    id = "carousel-preview",
    items = listOf(
        ComponentModel.SquaredCard(id = "sq-1", recipeId = "r1"),
        ComponentModel.SquaredCard(id = "sq-2", recipeId = "r2"),
        ComponentModel.SquaredCard(id = "sq-3", recipeId = "r3"),
    ),
)

@Preview(showBackground = true)
@Composable
private fun SduiCarouselPreview() {
    ChefAITheme {
        Surface {
            SduiCarousel(
                carousel = sampleCarousel,
                recipes = emptyMap(),
                onAction = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SduiCarouselDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface {
            SduiCarousel(
                carousel = sampleCarousel,
                recipes = emptyMap(),
                onAction = {},
            )
        }
    }
}
