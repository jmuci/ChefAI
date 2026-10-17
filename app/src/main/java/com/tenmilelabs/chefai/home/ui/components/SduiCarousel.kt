package com.tenmilelabs.chefai.home.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.ui.HomeAction
import java.util.UUID

/**
 * Renders a [ComponentModel.Carousel].
 *
 * A carousel made only of [ComponentModel.ListCard]s (see [rendersAsList]) stacks its rows
 * vertically: a list card is a full-width row whose text column takes the width left after the
 * thumbnail, so inside a [LazyRow]'s unbounded width that column measures to zero and only the
 * thumbnails show. Every other carousel scrolls horizontally.
 */
@Composable
fun SduiCarousel(
    carousel: ComponentModel.Carousel,
    recipes: Map<String, RecipePreview>,
    onAction: (HomeAction) -> Unit,
    bookmarkedRecipeIds: Set<UUID> = emptySet(),
    modifier: Modifier = Modifier,
) {
    if (carousel.rendersAsList) {
        // Not lazy: this already sits inside Home's LazyColumn, and a list section is a handful
        // of rows. 12dp + the row's own 4dp inset lines the thumbnails up with the header text.
        Column(modifier = modifier.padding(horizontal = 12.dp)) {
            carousel.items.forEach { item ->
                key(item.id) {
                    ComponentRenderer(
                        component = item,
                        recipes = recipes,
                        bookmarkedRecipeIds = bookmarkedRecipeIds,
                        onAction = onAction,
                    )
                }
            }
        }
        return
    }

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
                    bookmarkedRecipeIds = bookmarkedRecipeIds,
                    onAction = onAction,
                    isCarouselItem = true,
                )
            }
        }
    }
}

/** True when every item is a [ComponentModel.ListCard] — such a carousel renders as a vertical list. */
internal val ComponentModel.Carousel.rendersAsList: Boolean
    get() = items.isNotEmpty() && items.all { it is ComponentModel.ListCard }

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

private val sampleListRecipes = listOf(
    PreviewData.salmonTerRecipe,
    PreviewData.shrimpCevicheRecipe,
    PreviewData.codWithLemonRecipe,
).associateBy { it.uuid.toString() }

private val sampleListCarousel = ComponentModel.Carousel(
    id = "list-carousel-preview",
    items = sampleListRecipes.keys.map { ComponentModel.ListCard(id = "list-$it", recipeId = it) },
)

@Preview(name = "SduiCarousel — list cards, Light", showBackground = true)
@Preview(
    name = "SduiCarousel — list cards, Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SduiCarouselListCardsPreview() {
    ChefAITheme {
        Surface {
            SduiCarousel(
                carousel = sampleListCarousel,
                recipes = sampleListRecipes,
                onAction = {},
            )
        }
    }
}
