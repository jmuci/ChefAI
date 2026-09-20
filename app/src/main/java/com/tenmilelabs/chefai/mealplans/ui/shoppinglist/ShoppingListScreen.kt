package com.tenmilelabs.chefai.mealplans.ui.shoppinglist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.components.flat.SectionRule
import com.tenmilelabs.chefai.core.ui.navigation.ChefAITopAppBarSurface
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.GrocerySection
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingList
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListItem
import com.tenmilelabs.chefai.mealplans.domain.shoppinglist.ShoppingListSection
import com.tenmilelabs.chefai.mealplans.ui.shoppinglist.components.ShoppingListRow

@Composable
fun ShoppingListScreen(
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    viewModel: ShoppingListViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ShoppingListEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    when (val state = uiState) {
        is ShoppingListUiState.Loading -> LoadingContent(modifier = modifier)
        is ShoppingListUiState.NotFound -> EmptyContent(
            title = R.string.meal_plan_not_found,
            subtitle = R.string.meal_plan_not_found_subtitle,
            noRecipesIconRes = R.drawable.ic_skillet_cooktop_24dp,
            modifier = modifier,
        )
        is ShoppingListUiState.Success -> if (state.list.isEmpty) {
            EmptyContent(
                title = R.string.shopping_list_empty_title,
                subtitle = R.string.shopping_list_empty_subtitle,
                noRecipesIconRes = R.drawable.ic_skillet_cooktop_24dp,
                modifier = modifier,
            )
        } else {
            ShoppingListContent(
                list = state.list,
                onToggleItem = viewModel::onToggleItem,
                modifier = modifier,
            )
        }
    }
}

/**
 * The picked-up summary and its progress bar. Built on [ChefAITopAppBarSurface] as a bare slot,
 * the way the wizard's step bar stacks under its own title row — the screen's own back, title and
 * ghost "Uncheck all" button live in the app shell's header, above this.
 */
@Composable
private fun ShoppingListContent(
    list: ShoppingList,
    onToggleItem: (ShoppingListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ShoppingListProgressHeader(list = list)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            list.sections.forEachIndexed { sectionIndex, section ->
                item(key = "header-${section.section.name}") {
                    if (sectionIndex > 0) Spacer(Modifier.height(16.dp))
                    SectionHeader(section = section)
                }
                item(key = "rule-top-${section.section.name}") { SectionRule() }
                itemsIndexed(
                    items = section.items,
                    key = { _, item -> "item-${item.key}" },
                ) { index, item ->
                    if (index > 0) RowRule()
                    ShoppingListRow(
                        name = item.displayName,
                        quantityLabel = item.quantityLabel,
                        isApproximate = item.isApproximate,
                        isChecked = item.isChecked,
                        checkedByName = item.checkedByName,
                        onToggle = { onToggleItem(item) },
                    )
                }
                item(key = "rule-bottom-${section.section.name}") { SectionRule() }
            }
        }
    }
}

@Composable
private fun ShoppingListProgressHeader(
    list: ShoppingList,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(targetValue = list.progress, label = "shoppingProgress")

    ChefAITopAppBarSurface(modifier = modifier) {
        Text(
            text = stringResource(R.string.shopping_list_progress, list.checkedCount, list.totalCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.chefColors.neutral.s200),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    section: ShoppingListSection,
    modifier: Modifier = Modifier,
) {
    Text(
        text = section.section.label.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.chefColors.accentText,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// region Previews

private fun previewList(): ShoppingList = ShoppingList(
    sections = listOf(
        ShoppingListSection(
            section = GrocerySection.PRODUCE,
            items = listOf(
                ShoppingListItem("broccoli", "Broccoli", "2 heads", GrocerySection.PRODUCE, isChecked = true, checkedByName = "Ana"),
                ShoppingListItem("garlic", "Garlic", "1 bulb", GrocerySection.PRODUCE, isChecked = false),
                ShoppingListItem("dill", "Fresh dill", "1 bunch", GrocerySection.PRODUCE, isChecked = false),
            ),
        ),
        ShoppingListSection(
            section = GrocerySection.MEAT_AND_SEAFOOD,
            items = listOf(
                ShoppingListItem("salmon", "Salmon fillets", "6 pcs", GrocerySection.MEAT_AND_SEAFOOD, isChecked = true),
                ShoppingListItem("beef", "Beef mince", "900 g", GrocerySection.MEAT_AND_SEAFOOD, isChecked = false, isApproximate = true),
            ),
        ),
        ShoppingListSection(
            section = GrocerySection.DAIRY_AND_EGGS,
            items = listOf(
                ShoppingListItem("pecorino", "Pecorino romano", "150 g", GrocerySection.DAIRY_AND_EGGS, isChecked = false),
                ShoppingListItem("eggs", "Eggs", "6", GrocerySection.DAIRY_AND_EGGS, isChecked = false),
            ),
        ),
    ),
)

@Preview(name = "Shopping list — Light", showBackground = true, heightDp = 700)
@Composable
private fun ShoppingListContentLightPreview() {
    ChefAITheme(darkTheme = false) {
        ShoppingListContent(
            list = previewList(),
            onToggleItem = {},
        )
    }
}

@Preview(name = "Shopping list — Dark", showBackground = true, heightDp = 700)
@Composable
private fun ShoppingListContentDarkPreview() {
    ChefAITheme(darkTheme = true) {
        ShoppingListContent(
            list = previewList(),
            onToggleItem = {},
        )
    }
}

@Preview(name = "Shopping list — empty", showBackground = true, heightDp = 500)
@Composable
private fun ShoppingListEmptyPreview() {
    ChefAITheme(darkTheme = false) {
        EmptyContent(
            title = R.string.shopping_list_empty_title,
            subtitle = R.string.shopping_list_empty_subtitle,
            noRecipesIconRes = R.drawable.ic_skillet_cooktop_24dp,
        )
    }
}

// endregion
