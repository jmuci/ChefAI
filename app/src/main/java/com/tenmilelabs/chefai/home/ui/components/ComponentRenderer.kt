package com.tenmilelabs.chefai.home.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.LargeCard
import com.tenmilelabs.chefai.core.ui.components.RecipeListCard
import com.tenmilelabs.chefai.core.ui.components.SectionHeaderWithSubtitle
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.ui.HomeAction

/**
 * Routes a [ComponentModel] to its Compose representation.
 *
 * Card types ([ComponentModel.LargeCard], [ComponentModel.SquaredCard], [ComponentModel.ListCard])
 * look up their recipe data from [recipes] by [ComponentModel.LargeCard.recipeId]. If the recipe
 * is not yet in Room (e.g. first launch before sync), the card is silently skipped.
 */
@Composable
fun ComponentRenderer(
    component: ComponentModel,
    recipes: Map<String, RecipePreview>,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
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
                onAction = onAction,
                modifier = modifier,
            )
        }
        is ComponentModel.LargeCard -> {
            val recipe = component.recipeId?.let { recipes[it] } ?: return
            LargeCard(
                recipe = recipe,
                onClick = { onAction(HomeAction.CardClicked(it.toString())) },
                modifier = modifier.width(300.dp).height(220.dp),
            )
        }
        is ComponentModel.SquaredCard -> {
            val recipe = component.recipeId?.let { recipes[it] } ?: return
            LargeCard(
                recipe = recipe,
                onClick = { onAction(HomeAction.CardClicked(it.toString())) },
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
