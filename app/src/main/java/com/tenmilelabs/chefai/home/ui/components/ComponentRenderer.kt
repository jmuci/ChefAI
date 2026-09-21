package com.tenmilelabs.chefai.home.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.LargeCard
import com.tenmilelabs.chefai.core.ui.components.RecipeListCard
import com.tenmilelabs.chefai.core.ui.components.SectionHeaderWithSubtitle
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.ui.HomeAction
import java.util.UUID

/**
 * Routes a [ComponentModel] to its Compose representation.
 *
 * Card types ([ComponentModel.LargeCard], [ComponentModel.SquaredCard], [ComponentModel.ListCard])
 * look up their recipe data from [recipes] by [ComponentModel.LargeCard.recipeId]. If the recipe
 * is not yet in Room (e.g. first launch before sync), the card is silently skipped.
 *
 * @param isCarouselItem Whether [component] is being rendered as a horizontally-scrolling
 * carousel tile ([SduiCarousel]) rather than a standalone row in the top-level list ([HomeContent]
 * calls this directly for each component, [SduiCarousel] recurses into it for its items). A
 * [ComponentModel.LargeCard] outside a carousel is the design's "Tonight" hero (01) and takes the
 * full row width instead of a fixed carousel-tile size; nothing else about the routing changes.
 */
@Composable
fun ComponentRenderer(
    component: ComponentModel,
    recipes: Map<String, RecipePreview>,
    onAction: (HomeAction) -> Unit,
    bookmarkedRecipeIds: Set<UUID> = emptySet(),
    modifier: Modifier = Modifier,
    isCarouselItem: Boolean = false,
) {
    when (component) {
        is ComponentModel.SectionHeader -> {
            SectionHeaderWithSubtitle(
                title = component.title,
                subtitle = component.subtitle ?: "",
            )
        }
        is ComponentModel.Carousel -> {
            SduiCarousel(
                carousel = component,
                recipes = recipes,
                bookmarkedRecipeIds = bookmarkedRecipeIds,
                onAction = onAction,
                modifier = modifier,
            )
        }
        is ComponentModel.LargeCard -> {
            val recipe = component.recipeId?.let { recipes[it] } ?: return
            LargeCard(
                recipe = recipe,
                isInCollection = recipe.uuid in bookmarkedRecipeIds,
                onClick = { onAction(HomeAction.CardClicked(it.toString())) },
                onSaveToCollection = { onAction(HomeAction.BookmarkToggled(it)) },
                modifier = if (isCarouselItem) {
                    modifier.width(300.dp).height(220.dp)
                } else {
                    // No fixed height here: unlike the carousel tile, the hero stacks tags,
                    // title, a meta row and a full-width button below the image, so it must
                    // wrap that content instead of clipping it to the tile's 220dp.
                    modifier.fillMaxWidth().padding(horizontal = 16.dp)
                },
            )
        }
        is ComponentModel.SquaredCard -> {
            val recipe = component.recipeId?.let { recipes[it] } ?: return
            LargeCard(
                recipe = recipe,
                isInCollection = recipe.uuid in bookmarkedRecipeIds,
                onClick = { onAction(HomeAction.CardClicked(it.toString())) },
                onSaveToCollection = { onAction(HomeAction.BookmarkToggled(it)) },
                modifier = modifier.size(180.dp),
            )
        }
        is ComponentModel.ListCard -> {
            val recipe = component.recipeId?.let { recipes[it] } ?: return
            RecipeListCard(
                recipe = recipe,
                navigateToDetail = { onAction(HomeAction.CardClicked(it.toString())) },
            )
        }
        is ComponentModel.Unknown -> { /* Silently skip -- no UI rendered */ }
    }
}

// -- Previews -----------------------------------------------------------------

private val previewRecipeId = PreviewData.grilledChickenRecipe.uuid.toString()
private val previewLargeCard = ComponentModel.LargeCard(id = "tonight", recipeId = previewRecipeId)
private val previewRecipes = mapOf(previewRecipeId to PreviewData.grilledChickenRecipe)

@Preview(name = "ComponentRenderer — Tonight hero, Light")
@Preview(name = "ComponentRenderer — Tonight hero, Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComponentRendererHeroPreview() {
    ChefAITheme {
        Surface {
            ComponentRenderer(
                component = previewLargeCard,
                recipes = previewRecipes,
                onAction = {},
            )
        }
    }
}
