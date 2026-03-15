package com.tenmilelabs.chefai.home.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.ui.components.ComponentRenderer
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
            onAction = viewModel::onAction,
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
        contentPadding = PaddingValues(vertical = dimensionResource(R.dimen.padding_extra_small)),
    ) {
        items(
            items = components,
            key = { component -> component.id },
        ) { component ->
            ComponentRenderer(
                component = component,
                recipes = recipes,
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
