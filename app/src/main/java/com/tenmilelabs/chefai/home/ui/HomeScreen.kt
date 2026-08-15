package com.tenmilelabs.chefai.home.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.ui.components.ComponentRenderer
import com.tenmilelabs.chefai.search.ui.RecipeSearchOverlay
import java.util.UUID

/**
 * Home screen entry point. Collects SDUI state from [HomeViewModel] and delegates
 * rendering to [HomeContent]. Navigation events are forwarded via [onRecipeClick].
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onRecipeClick: (UUID) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is HomeUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(event.message),
                        duration = SnackbarDuration.Short,
                    )
                }
                is HomeUiEvent.NavigateToRecipeDetail -> {
                    onRecipeClick(event.recipeUuid)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            HomeUiState.Loading -> LoadingContent()

            is HomeUiState.Error -> EmptyContent(
                title = state.message,
                subtitle = R.string.no_recipes_subtitle,
                noRecipesIconRes = R.drawable.ic_chef_hat_black_24dp,
            )

            is HomeUiState.Success -> HomeContent(
                components = state.components,
                recipes = state.recipes,
                bookmarkedRecipeIds = state.bookmarkedRecipeIds,
                onAction = viewModel::onAction,
            )
        }

        // Not a LazyColumn item — its expanded state must not scroll away or fight the list.
        RecipeSearchOverlay(
            snackbarHostState = snackbarHostState,
            onRecipeClick = onRecipeClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(R.dimen.padding_small)),
        )
    }
}

/**
 * Stateless content composable. Renders an ordered list of SDUI [ComponentModel]s
 * inside a [LazyColumn] with stable keys for smooth animated updates on refresh.
 * Recipe data is passed down as a [Map] and looked up by each card component.
 */
@Composable
fun HomeContent(
    components: List<ComponentModel>,
    recipes: Map<String, RecipePreview>,
    bookmarkedRecipeIds: Set<UUID> = emptySet(),
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (components.isEmpty()) {
        EmptyContent(
            title = R.string.error_loading_home,
            subtitle = R.string.no_recipes_subtitle,
            noRecipesIconRes = R.drawable.ic_chef_hat_black_24dp,
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .testTag("HomeScreen")
            .fillMaxSize()
            .animateContentSize(),
        // Extra top clearance so the collapsed search bar (a Box overlay, not a list item —
        // see HomeScreen's RecipeSearchOverlay) doesn't sit on top of the first section's text.
        contentPadding = PaddingValues(
            top = 64.dp,
            bottom = dimensionResource(R.dimen.padding_extra_small),
        ),
    ) {
        items(
            items = components,
            key = { component -> component.id },
        ) { component ->
            ComponentRenderer(
                component = component,
                recipes = recipes,
                bookmarkedRecipeIds = bookmarkedRecipeIds,
                onAction = onAction,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenLoadingPreview() {
    ChefAITheme {
        LoadingContent()
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    ChefAITheme {
        EmptyContent(
            title = R.string.no_recipes_title,
            subtitle = R.string.no_recipes_subtitle,
            noRecipesIconRes = R.drawable.ic_chef_hat_black_24dp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenDarkEmptyPreview() {
    ChefAITheme(darkTheme = true) {
        EmptyContent(
            title = R.string.no_recipes_title,
            subtitle = R.string.no_recipes_subtitle,
            noRecipesIconRes = R.drawable.ic_chef_hat_black_24dp,
        )
    }
}
