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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
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
import com.tenmilelabs.chefai.mealplans.ui.shoppinglist.components.ShoppingListRowAction

/** What the list's content asks of the ViewModel. */
sealed interface ShoppingListAction {
    data class Toggle(val item: ShoppingListItem) : ShoppingListAction
    data class Rename(val item: ShoppingListItem, val newName: String) : ShoppingListAction
    data class Remove(val item: ShoppingListItem) : ShoppingListAction
}

@Composable
fun ShoppingListScreen(
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    viewModel: ShoppingListViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ShoppingListEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                // Launched, not awaited: a snackbar with an action suspends until it is dismissed,
                // and the next event (another removal, an error) must not queue behind it.
                is ShoppingListEvent.ItemRemoved -> launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.shopping_list_item_removed, event.displayName),
                        actionLabel = resources.getString(R.string.shopping_list_undo),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove(event.itemKey)
                }
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
                onAction = { action ->
                    when (action) {
                        is ShoppingListAction.Toggle -> viewModel.onToggleItem(action.item)
                        is ShoppingListAction.Rename -> viewModel.onRenameItem(action.item, action.newName)
                        is ShoppingListAction.Remove -> viewModel.onDeleteItem(action.item)
                    }
                },
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
internal fun ShoppingListContent(
    list: ShoppingList,
    onAction: (ShoppingListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which row shows its pencil/X, and which is being renamed — transient view state, keyed by
    // item key so it survives rotation and the list recomputing underneath it.
    var revealedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var renamingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val renaming = renamingKey?.let { key ->
        list.sections.flatMap { it.items }.firstOrNull { it.key == key }
    }

    renaming?.let { item ->
        RenameItemDialog(
            initialName = item.displayName,
            onConfirm = { newName ->
                onAction(ShoppingListAction.Rename(item, newName))
                renamingKey = null
            },
            onDismiss = { renamingKey = null },
        )
    }

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
                        actionsVisible = revealedKey == item.key,
                        onAction = { rowAction ->
                            when (rowAction) {
                                // With the pencil and X showing, a tap puts them away rather than
                                // ticking the row — the way out of a long press by mistake.
                                ShoppingListRowAction.Toggle ->
                                    if (revealedKey == item.key) {
                                        revealedKey = null
                                    } else {
                                        onAction(ShoppingListAction.Toggle(item))
                                    }
                                ShoppingListRowAction.LongPress ->
                                    revealedKey = if (revealedKey == item.key) null else item.key
                                ShoppingListRowAction.Rename -> {
                                    revealedKey = null
                                    renamingKey = item.key
                                }
                                ShoppingListRowAction.Remove -> {
                                    revealedKey = null
                                    onAction(ShoppingListAction.Remove(item))
                                }
                            }
                        },
                    )
                }
                item(key = "rule-bottom-${section.section.name}") { SectionRule() }
            }
        }
    }
}

@Composable
private fun RenameItemDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val canSave = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_list_rename_title)) },
        text = {
            FlatField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.shopping_list_rename_label),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (canSave) onConfirm(name) },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = canSave) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        },
    )
}

@Composable
private fun ShoppingListProgressHeader(
    list: ShoppingList,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(targetValue = list.progress, label = "shoppingProgress")

    ChefAITopAppBarSurface(modifier = modifier, applyStatusBarInsets = false) {
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
            onAction = {},
        )
    }
}

@Preview(name = "Shopping list — Dark", showBackground = true, heightDp = 700)
@Composable
private fun ShoppingListContentDarkPreview() {
    ChefAITheme(darkTheme = true) {
        ShoppingListContent(
            list = previewList(),
            onAction = {},
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
