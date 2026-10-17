package com.tenmilelabs.chefai.home.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.components.Panel
import com.tenmilelabs.chefai.core.ui.components.PanelMetrics
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.ui.components.ComponentRenderer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
            bookmarkedRecipeIds = state.bookmarkedRecipeIds,
            onAction = viewModel::onAction,
        )
    }
}

/**
 * Stateless content composable. Renders an ordered list of SDUI [ComponentModel]s
 * inside a [LazyColumn] with stable keys for smooth animated updates on refresh.
 * Recipe data is passed down as a [Map] and looked up by each card component.
 *
 * Each section (a header and the components after it, see [groupIntoSections]) sits on its own
 * rounded panel over a slightly darker page — sections separate by color and space, not rules.
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

    val sections = remember(components) { groupIntoSections(components) }

    LazyColumn(
        modifier = modifier
            .testTag("HomeScreen")
            .fillMaxSize()
            .background(MaterialTheme.chefColors.panelGround)
            .animateContentSize(),
        contentPadding = PaddingValues(
            start = PanelMetrics.Inset,
            end = PanelMetrics.Inset,
            bottom = PanelMetrics.Gap,
        ),
        verticalArrangement = Arrangement.spacedBy(PanelMetrics.Gap),
    ) {
        items(
            items = sections,
            key = { section -> section.key },
        ) { section ->
            Panel(modifier = Modifier.animateItem()) {
                section.components.forEach { component ->
                    ComponentRenderer(
                        component = component,
                        recipes = recipes,
                        bookmarkedRecipeIds = bookmarkedRecipeIds,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

/**
 * "Wednesday, August 12" — today's date, shown under the "ChefAI" wordmark. Exposed so the app
 * shell's header, which owns the Home top bar, can build the same subtitle it shows next to the
 * title (see `mealPlanDetailSubtitle` for the equivalent on the meal-plan-detail header).
 */
fun homeDateSubtitle(): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    return formatter.format(LocalDate.now())
}

private val previewHomeRecipes = listOf(
    PreviewData.grilledChickenRecipe,
    PreviewData.carbonaraPasta,
    PreviewData.salmonTerRecipe,
).associateBy { it.uuid.toString() }

private val previewHomeComponents = previewHomeRecipes.keys.toList().let { ids ->
    listOf(
        ComponentModel.SectionHeader(id = "for-you", title = "For You", subtitle = "Personalized recipe picks"),
        ComponentModel.Carousel(
            id = "for-you-carousel",
            items = ids.map { ComponentModel.LargeCard(id = "large-$it", recipeId = it) },
        ),
        ComponentModel.SectionHeader(id = "seafood", title = "Seafood", subtitle = "From the ocean to your plate"),
        ComponentModel.Carousel(
            id = "seafood-carousel",
            items = ids.map { ComponentModel.ListCard(id = "list-$it", recipeId = it) },
        ),
    )
}

@Preview(name = "Home — panels, light", heightDp = 720)
@Preview(name = "Home — panels, dark", heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentPreview() {
    ChefAITheme {
        HomeContent(
            components = previewHomeComponents,
            recipes = previewHomeRecipes,
            onAction = {},
        )
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
