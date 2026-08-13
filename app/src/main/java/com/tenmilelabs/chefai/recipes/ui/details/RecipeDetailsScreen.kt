package com.tenmilelabs.chefai.recipes.ui.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.room.relations.RecipeIngredient
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.ui.components.InfoChip
import com.tenmilelabs.chefai.core.ui.components.InfoChipType
import com.tenmilelabs.chefai.core.ui.components.RecipeTimeRow
import com.tenmilelabs.chefai.core.ui.preview.RecipeData
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.core.util.MathUtils
import com.tenmilelabs.chefai.recipes.ui.components.DeleteConfirmationDialog
import kotlinx.coroutines.launch
import timber.log.Timber


@Composable
fun RecipeDetailsScreen(
    viewModel: RecipeDetailsViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onEditClick: ((java.util.UUID) -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val recipeDeletedText = stringResource(R.string.recipe_deleted)
    LaunchedEffect(viewModel, snackbarHostState, onNavigateBack, recipeDeletedText) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RecipeDetailsEffect.RecipeDeleted -> {
                    snackbarHostState.showSnackbar(
                        message = recipeDeletedText,
                        duration = SnackbarDuration.Short,
                    )
                    onNavigateBack?.invoke()
                }
            }
        }
    }

    if (uiState.isLoading) {
        LoadingContent()
    } else {
        if (uiState.recipe != null) {
            RecipeDetailsContent(
                recipe = uiState.recipe!!,
                onEditClick = onEditClick?.let { callback ->
                    { callback(viewModel.recipeUuid) }
                },
                isBookmarked = uiState.isBookmarked,
                onToggleBookmark = viewModel::toggleBookmark,
                onDeleteClick = onNavigateBack?.let { { viewModel.onDeleteClick() } },
                showDeleteConfirmation = uiState.showDeleteConfirmation,
                onConfirmDelete = viewModel::confirmDelete,
                onDismissDeleteDialog = viewModel::dismissDeleteDialog,
                isDeleting = uiState.isDeleting,
            )
        } else {
            EmptyContent(
                title = R.string.recipe_not_found_error,
                subtitle = R.string.recipe_not_found_error_subtitle,
                noRecipesIconRes = R.drawable.ic_chef_hat_black_24dp
            )
            Timber.e("Recipe Not Found Loading error!")
        }
    }

    // Check for user messages to display on the screen
    uiState.userMessage?.let { message ->
        val snackbarText = stringResource(message)
        LaunchedEffect(snackbarHostState, viewModel, message, snackbarText) {
            snackbarHostState.showSnackbar(
                message = snackbarText,
                duration = SnackbarDuration.Short
            )
            viewModel.snackbarMessageShown()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecipeDetailsContent(
    recipe: Recipe,
    onEditClick: (() -> Unit)? = null,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null,
    showDeleteConfirmation: Boolean = false,
    onConfirmDelete: () -> Unit = {},
    onDismissDeleteDialog: () -> Unit = {},
    isDeleting: Boolean = false,
) {
    if (showDeleteConfirmation) {
        DeleteConfirmationDialog(
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDeleteDialog,
        )
    }

    val tabTitles = listOf(stringResource(R.string.ingredients), stringResource(R.string.steps))
    val pagerState = rememberPagerState { tabTitles.size }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.padding_medium))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimensionResource(id = R.dimen.padding_medium)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(
                            if (isBookmarked) R.string.remove_from_collection_content_description
                            else R.string.save_to_collection_content_description
                        ),
                        tint = if (isBookmarked) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDeleteClick != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        enabled = !isDeleting,
                        modifier = Modifier.testTag("DeleteRecipeButton"),
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_recipe_button),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            AsyncImage(
                model = recipeImageModel(recipe.localImagePath, recipe.imageUrl),
                placeholder = painterResource(R.drawable.ic_img_placeholder),
                error = painterResource(R.drawable.ic_img_error),
                contentDescription = stringResource(R.string.recipe_image_content_description),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = dimensionResource(id = R.dimen.padding_small))
                    .height(200.dp)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_small)))
            RecipeTimeRow(recipe.prepTimeMinutes, recipe.cookTimeMinutes)
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_small)))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_extra_small)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_extra_small))
            ) {
                recipe.labels.forEach { label ->
                    InfoChip(text = label.displayName, type = InfoChipType.LABEL)
                }
                recipe.tags.forEach { tag ->
                    InfoChip(text = tag.displayName, type = InfoChipType.TAG)
                }
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_medium)))
            if (!recipe.description.isEmpty()) {
                RecipeDescription(recipe.description)
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_medium)))
            }
        }

        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(text = title) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) { page ->
            when (page) {
                0 -> IngredientsList(ingredients = recipe.ingredients)
                1 -> StepsList(steps = recipe.steps)
            }
        }
    }

    if (onEditClick != null) {
        FloatingActionButton(
            onClick = onEditClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.edit_button),
            )
        }
    }
    } // Box
}

@Composable
private fun RecipeDescription(description: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(id = R.dimen.padding_medium)),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_medium))
        )
    }
}

@Composable
fun IngredientsList(ingredients: List<RecipeIngredient>) {
    LazyColumn(contentPadding = PaddingValues(all = dimensionResource(id = R.dimen.padding_medium))) {
        items(ingredients) { ingredient ->
            Row() {
                Text(
                    text = ingredient.ingredientDisplayName,
                    modifier = Modifier
                        .padding(vertical = dimensionResource(id = R.dimen.padding_small)),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = MathUtils.removeTrailingZeros(ingredient.quantity.toString()) + " " + ingredient.unit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dimensionResource(id = R.dimen.padding_small)),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun StepsList(steps: List<RecipeStep>) {
    LazyColumn(contentPadding = PaddingValues(all = dimensionResource(id = R.dimen.padding_medium))) {
        items(steps.sortedBy { it.orderIndex }) { step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(id = R.dimen.padding_medium)),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${step.orderIndex}.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = dimensionResource(id = R.dimen.padding_small))
                )
                Text(text = step.instruction, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsFullScreenPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe, isBookmarked = false)
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsBookmarkedPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe, isBookmarked = true)
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsDeleteConfirmationPreview() {
    ChefAITheme {
        RecipeDetailsContent(
            recipe = RecipeData.recipe,
            onDeleteClick = {},
            showDeleteConfirmation = true,
        )
    }
}
