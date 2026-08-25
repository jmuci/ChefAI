package com.tenmilelabs.chefai.mealplans.ui.shoppinglist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
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
                planName = state.planName,
                list = state.list,
                onToggleItem = viewModel::onToggleItem,
                onUncheckAll = viewModel::onUncheckAll,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingListContent(
    planName: String,
    list: ShoppingList,
    onToggleItem: (ShoppingListItem) -> Unit,
    onUncheckAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ShoppingListHeader(planName = planName, list = list, onUncheckAll = onUncheckAll)

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        ) {
            list.sections.forEach { section ->
                stickyHeader(key = "header-${section.section.name}") {
                    SectionHeader(section = section)
                }
                items(
                    items = section.items,
                    key = { "item-${it.key}" },
                ) { item ->
                    ShoppingListRow(
                        name = item.displayName,
                        quantityLabel = item.quantityLabel,
                        isChecked = item.isChecked,
                        onToggle = { onToggleItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShoppingListHeader(
    planName: String,
    list: ShoppingList,
    onUncheckAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(targetValue = list.progress, label = "shoppingProgress")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = planName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                if (list.checkedCount > 0) {
                    TextButton(onClick = onUncheckAll) {
                        Text(stringResource(R.string.shopping_list_uncheck_all))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.shopping_list_progress, list.checkedCount, list.totalCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    section: ShoppingListSection,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${section.section.emoji}  ${section.section.label}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${section.checkedCount}/${section.items.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// region Previews

private fun previewList(): ShoppingList = ShoppingList(
    sections = listOf(
        ShoppingListSection(
            section = GrocerySection.PRODUCE,
            items = listOf(
                ShoppingListItem("banana", "Banana", "3", GrocerySection.PRODUCE, isChecked = false),
                ShoppingListItem("onion", "Onion", "2", GrocerySection.PRODUCE, isChecked = true),
            ),
        ),
        ShoppingListSection(
            section = GrocerySection.DAIRY_AND_EGGS,
            items = listOf(
                ShoppingListItem("milk", "Milk", "1 l", GrocerySection.DAIRY_AND_EGGS, isChecked = false),
            ),
        ),
    ),
)

@Preview(name = "Shopping list — Light", showBackground = true, heightDp = 700)
@Composable
private fun ShoppingListContentLightPreview() {
    ChefAITheme(darkTheme = false) {
        ShoppingListContent(
            planName = "3-day meal plan",
            list = previewList(),
            onToggleItem = {},
            onUncheckAll = {},
        )
    }
}

@Preview(name = "Shopping list — Dark", showBackground = true, heightDp = 700)
@Composable
private fun ShoppingListContentDarkPreview() {
    ChefAITheme(darkTheme = true) {
        ShoppingListContent(
            planName = "3-day meal plan",
            list = previewList(),
            onToggleItem = {},
            onUncheckAll = {},
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
