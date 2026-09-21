package com.tenmilelabs.chefai.recipes.ui.details

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.domain.model.Recipe
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.domain.units.IngredientAmountFormatter
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.ui.components.CookedToggleButton
import com.tenmilelabs.chefai.core.ui.components.InfoChip
import com.tenmilelabs.chefai.core.ui.components.InfoChipType
import com.tenmilelabs.chefai.core.ui.components.RecipePrivacyBadge
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatCheckbox
import com.tenmilelabs.chefai.core.ui.components.flat.RuledGroup
import com.tenmilelabs.chefai.core.ui.components.flat.SectionRule
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.components.flat.flatToggleable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.preview.RecipeData
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.core.ui.timer.RecipeTimerViewModel
import com.tenmilelabs.chefai.core.ui.timer.rememberNotificationPermissionRequester
import com.tenmilelabs.chefai.core.util.EmptyContent
import com.tenmilelabs.chefai.core.util.LoadingContent
import com.tenmilelabs.chefai.core.util.parseStepDurationSeconds
import com.tenmilelabs.chefai.recipes.domain.scaling.RecipeScaling
import com.tenmilelabs.chefai.recipes.ui.components.DeleteConfirmationDialog
import com.tenmilelabs.chefai.recipes.ui.details.components.RecipeStatsBar
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
        val recipe = uiState.recipe
        if (recipe != null) {
            RecipeDetailsContent(
                recipe = recipe,
                onAction = { action ->
                    when (action) {
                        RecipeDetailsAction.EditClicked -> onEditClick?.invoke(viewModel.recipeUuid)
                        RecipeDetailsAction.ToggleBookmark -> viewModel.toggleBookmark()
                        RecipeDetailsAction.DeleteClicked -> viewModel.onDeleteClick()
                        RecipeDetailsAction.ConfirmDelete -> viewModel.confirmDelete()
                        RecipeDetailsAction.DismissDeleteDialog -> viewModel.dismissDeleteDialog()
                        RecipeDetailsAction.ToggleCooked -> viewModel.onToggleCooked()
                        is RecipeDetailsAction.ServingsChanged ->
                            viewModel.onServingsChange(action.servings)
                        // Neither flow is designed yet (no meal-plan picker, no per-recipe grocery
                        // list) — see the GitHub issue filed alongside this screen's redesign.
                        RecipeDetailsAction.AddToMealPlanClicked,
                        RecipeDetailsAction.AddToGroceryListClicked -> Unit
                        RecipeDetailsAction.NavigateBack -> onNavigateBack?.invoke()
                    }
                },
                servings = uiState.servings,
                isBookmarked = uiState.isBookmarked,
                canEdit = onEditClick != null,
                // Deleting navigates away, so the button is only offered when there is somewhere to
                // go — and even then, only for a recipe the user owns (uiState.canDelete).
                canDelete = onNavigateBack != null && uiState.canDelete,
                showDeleteConfirmation = uiState.showDeleteConfirmation,
                isDeleting = uiState.isDeleting,
                measurementSystem = uiState.measurementSystem,
                showCookedToggle = uiState.showCookedToggle,
                isCooked = uiState.isCooked,
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

/**
 * The recipe details screen, stateless.
 *
 * @param servings the portions the ingredient list is shown at; the quantities are scaled from
 *   [recipe] to match, here rather than by the caller, so the stepper and the list can never
 *   disagree about what they are showing.
 * @param measurementSystem the units the ingredient list is read in. Applied here, after scaling,
 *   for the same reason scaling itself is: it is a way of reading the recipe, never an edit to it.
 * @param canEdit whether the caller has somewhere for the edit FAB to go.
 * @param canDelete whether the caller has somewhere to navigate after a delete, and the current
 *   user owns [recipe] (created or imported it themselves).
 */
@Composable
fun RecipeDetailsContent(
    recipe: Recipe,
    onAction: (RecipeDetailsAction) -> Unit = {},
    servings: ServingsUiState = ServingsUiState.forRecipeServings(recipe.servings),
    measurementSystem: MeasurementSystem = MeasurementSystem.DEFAULT,
    isBookmarked: Boolean = false,
    canEdit: Boolean = false,
    canDelete: Boolean = false,
    showDeleteConfirmation: Boolean = false,
    isDeleting: Boolean = false,
    showCookedToggle: Boolean = false,
    isCooked: Boolean = false,
) {
    if (showDeleteConfirmation) {
        DeleteConfirmationDialog(
            onConfirm = { onAction(RecipeDetailsAction.ConfirmDelete) },
            onDismiss = { onAction(RecipeDetailsAction.DismissDeleteDialog) },
        )
    }

    // Scale first and convert second, never the other way round: scaling arithmetic run on an
    // already-converted, already-rounded value compounds the rounding.
    val ingredients = remember(
        recipe.ingredients, servings.base, servings.current, measurementSystem,
    ) {
        RecipeScaling.scale(
            ingredients = recipe.ingredients,
            baseServings = servings.base,
            targetServings = servings.current,
        ).map { ingredient ->
            val amount = IngredientAmountFormatter.format(
                quantity = ingredient.quantity,
                unit = ingredient.unit,
                ingredientName = ingredient.ingredientDisplayName,
                system = measurementSystem,
            )
            IngredientRowUi(
                name = ingredient.ingredientDisplayName,
                amountLabel = amount.text,
                isApproximate = amount.isApproximate,
            )
        }
    }

    // Local-only: checking off an ingredient while cooking has no server-side counterpart (the
    // handoff doesn't specify one either), so this is scoped to the composition, not the
    // ViewModel. Keyed by the ingredient list's identity so a re-scale or a unit-system change —
    // which rebuilds the list above but preserves row order — doesn't carry stale indices over
    // from a previous recipe.
    var checkedIngredients by rememberSaveable(ingredients) { mutableStateOf(emptySet<Int>()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Leaves room for the sticky footer so the last instruction row isn't hidden
                // behind it.
                .padding(bottom = FooterClearance),
        ) {
            RecipeHero(
                recipe = recipe,
                isBookmarked = isBookmarked,
                showCookedToggle = showCookedToggle,
                isCooked = isCooked,
                canDelete = canDelete,
                isDeleting = isDeleting,
                onAction = onAction,
            )

            Column(modifier = Modifier.padding(horizontal = ScreenHorizontalPadding)) {
                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RecipePrivacyBadge(privacy = recipe.privacy)
                    recipe.labels.forEach { label ->
                        InfoChip(text = label.displayName, type = InfoChipType.LABEL)
                    }
                    recipe.tags.forEach { tag ->
                        InfoChip(text = tag.displayName, type = InfoChipType.TAG)
                    }
                }

                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp),
                )

                if (recipe.description.isNotEmpty()) {
                    RecipeDescription(
                        description = recipe.description,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            RecipeStatsBar(
                prepTimeMinutes = recipe.prepTimeMinutes,
                cookTimeMinutes = recipe.cookTimeMinutes,
                servings = servings.current,
                servingsRange = servings.range,
                onServingsChange = { onAction(RecipeDetailsAction.ServingsChanged(it)) },
                isServingsEstimated = servings.isEstimated,
            )

            SectionHeader(
                text = stringResource(R.string.ingredients),
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            IngredientsList(
                ingredients = ingredients,
                checkedIndices = checkedIngredients,
                onToggle = { index ->
                    checkedIngredients = if (index in checkedIngredients) {
                        checkedIngredients - index
                    } else {
                        checkedIngredients + index
                    }
                },
            )

            SectionHeader(
                text = stringResource(R.string.recipe_section_instructions),
                modifier = Modifier.padding(
                    start = ScreenHorizontalPadding,
                    end = ScreenHorizontalPadding,
                    top = 16.dp,
                ),
            )
            StepsList(steps = recipe.steps)
        }

        RecipeDetailsFooter(
            onAddToMealPlanClick = { onAction(RecipeDetailsAction.AddToMealPlanClicked) },
            onAddToGroceryListClick = { onAction(RecipeDetailsAction.AddToGroceryListClicked) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (canEdit) {
            FloatingActionButton(
                onClick = { onAction(RecipeDetailsAction.EditClicked) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = FooterClearance + 16.dp),
            ) {
                Icon(
                    painter = painterResource(ChefAIIcons.SquarePen),
                    contentDescription = stringResource(R.string.edit_button),
                )
            }
        }
    }
}

/** Space reserved at the bottom of the scrolling content for the sticky footer. */
private val FooterClearance = 84.dp
private val ScreenHorizontalPadding = 16.dp

/**
 * The full-bleed 4:3 hero (05): photo, back and save icon buttons overlaid at the top
 * corners. The cooked toggle and delete — situational, and not drawn in the design — join the
 * overlay's trailing group rather than crowding a header row the design doesn't have.
 */
@Composable
private fun RecipeHero(
    recipe: Recipe,
    isBookmarked: Boolean,
    showCookedToggle: Boolean,
    isCooked: Boolean,
    canDelete: Boolean,
    isDeleting: Boolean,
    onAction: (RecipeDetailsAction) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = recipeImageModel(recipe.localImagePath, recipe.imageUrl),
            placeholder = painterResource(R.drawable.ic_img_placeholder),
            error = painterResource(R.drawable.ic_img_error),
            contentDescription = stringResource(R.string.recipe_image_content_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        HeroOverlayIconButton(
            icon = ChefAIIcons.ArrowLeft,
            contentDescription = stringResource(R.string.header_navigate_back),
            onClick = { onAction(RecipeDetailsAction.NavigateBack) },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showCookedToggle) {
                CookedToggleButton(
                    isCooked = isCooked,
                    onToggle = { onAction(RecipeDetailsAction.ToggleCooked) },
                )
            }
            if (canDelete) {
                HeroOverlayIconButton(
                    icon = ChefAIIcons.Trash,
                    contentDescription = stringResource(R.string.delete_recipe_button),
                    onClick = { onAction(RecipeDetailsAction.DeleteClicked) },
                    enabled = !isDeleting,
                    loading = isDeleting,
                    testTag = "DeleteRecipeButton",
                )
            }
            HeroOverlayIconButton(
                icon = if (isBookmarked) ChefAIIcons.BookmarkFilled else ChefAIIcons.Bookmark,
                contentDescription = stringResource(
                    if (isBookmarked) R.string.remove_from_collection_content_description
                    else R.string.save_to_collection_content_description
                ),
                onClick = { onAction(RecipeDetailsAction.ToggleBookmark) },
                tint = if (isBookmarked) MaterialTheme.chefColors.accentText else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * A 40dp ground-filled square button floated over a photo — the back arrow and the bookmark on
 * the hero. Filled rather than transparent so it reads against photos of any tone; a bare icon
 * with no backing shape would wash out over a light photo.
 */
@Composable
private fun HeroOverlayIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    testTag: String? = null,
) {
    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .size(HeroOverlayButtonSize)
            .background(MaterialTheme.colorScheme.background)
            .flatClickable(onClick = onClick, enabled = enabled && !loading, role = Role.Button)
            .border(
                width = MaterialTheme.chefColors.sectionRuleWidth,
                color = MaterialTheme.colorScheme.outline,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private val HeroOverlayButtonSize = 40.dp

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

/**
 * The sticky footer (05): a full-width primary "Add to Meal Plan" plus a secondary icon-only
 * button for the grocery list — neither flow is wired up yet (no meal-plan picker, no per-recipe
 * grocery list exists), so both are visual only. See the GitHub issue filed alongside this
 * screen's redesign.
 */
@Composable
private fun RecipeDetailsFooter(
    onAddToMealPlanClick: () -> Unit,
    onAddToGroceryListClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SectionRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlatBlockButton(
                text = stringResource(R.string.recipe_add_to_meal_plan),
                onClick = onAddToMealPlanClick,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .flatClickable(onClick = onAddToGroceryListClick, role = Role.Button)
                    .border(
                        width = MaterialTheme.chefColors.sectionRuleWidth,
                        color = MaterialTheme.colorScheme.outline,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(ChefAIIcons.ShoppingCart),
                    contentDescription = stringResource(
                        R.string.recipe_add_to_grocery_list_content_description,
                    ),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private const val DESCRIPTION_COLLAPSED_MAX_LINES = 4

@Composable
private fun RecipeDescription(description: String, modifier: Modifier = Modifier) {
    var isExpanded by rememberSaveable(description) { mutableStateOf(false) }
    var isOverflowing by remember(description) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (isExpanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!isExpanded) isOverflowing = result.hasVisualOverflow
            },
        )
        if (isOverflowing || isExpanded) {
            Text(
                text = stringResource(if (isExpanded) R.string.view_less else R.string.view_more),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.chefColors.accentText,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { isExpanded = !isExpanded },
            )
        }
    }
}

/** One ingredient row, already scaled, converted and rendered. */
data class IngredientRowUi(
    val name: String,
    val amountLabel: String,
    /** True when the amount rests on an assumed density; the row prefixes it with "≈". */
    val isApproximate: Boolean = false,
)

/**
 * The checklist (05): checkbox, name, quantity, 1dp row dividers — tappable to check off while
 * cooking. [checkedIndices] and [onToggle] are index-based; see the local-only state comment where
 * this is called from [RecipeDetailsContent].
 */
@Composable
fun IngredientsList(
    ingredients: List<IngredientRowUi>,
    checkedIndices: Set<Int> = emptySet(),
    onToggle: (Int) -> Unit = {},
) {
    if (ingredients.isEmpty()) {
        Text(
            text = stringResource(R.string.no_ingredients_listed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
        )
        return
    }
    RuledGroup(items = ingredients.withIndex().toList(), key = { it.index }) { (index, ingredient) ->
        val checked = index in checkedIndices
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .flatToggleable(checked = checked, onCheckedChange = { onToggle(index) })
                .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlatCheckbox(checked = checked, onCheckedChange = null)
            Text(
                text = ingredient.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier.weight(1f),
            )
            // "≈" marks a weight arrived at through a typical density rather than measured. A
            // screen reader gets the word rather than the glyph, which it would skip.
            val spokenAmount = if (ingredient.isApproximate) {
                stringResource(R.string.ingredient_amount_approximate, ingredient.amountLabel)
            } else {
                null
            }
            Text(
                text = if (ingredient.isApproximate) "≈ ${ingredient.amountLabel}" else ingredient.amountLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.then(
                    if (spokenAmount != null) {
                        Modifier.semantics { contentDescription = spokenAmount }
                    } else {
                        Modifier
                    },
                ),
            )
        }
    }
}

@Composable
fun StepsList(steps: List<RecipeStep>) {
    if (steps.isEmpty()) {
        Text(
            text = stringResource(R.string.no_steps_listed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
        )
        return
    }
    // hiltViewModel() has no Hilt component to resolve against in Compose Preview and crashes
    // there; steps.forEach below still renders the list (minus a working timer button) in previews.
    val timerViewModel: RecipeTimerViewModel? =
        if (LocalInspectionMode.current) null else hiltViewModel()
    val requestNotificationPermission = rememberNotificationPermissionRequester()
    val context = LocalContext.current
    val timerReplacedMessage = stringResource(R.string.step_timer_replaced_message)

    val orderedSteps = steps.sortedBy { it.orderIndex }
    RuledGroup(items = orderedSteps, key = { it.uuid }) { step ->
        val stepLabel = stringResource(R.string.step_timer_label_format, step.orderIndex + 1)
        StepListItem(
            step = step,
            onStartTimer = { totalSeconds ->
                requestNotificationPermission()
                val replaced = timerViewModel?.start(
                    stepLabel = stepLabel,
                    totalSeconds = totalSeconds,
                )
                if (replaced != null) {
                    val message = timerReplacedMessage.format(replaced.stepLabel)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

/** 26dp accent-filled square number badge, per the handoff's numbered-instruction spec. */
private val StepBadgeSize = 26.dp

@Composable
private fun StepListItem(step: RecipeStep, onStartTimer: (Long) -> Unit) {
    val durationSeconds = remember(step.instruction) { parseStepDurationSeconds(step.instruction) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(StepBadgeSize)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (step.orderIndex + 1).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = step.instruction,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (durationSeconds != null) {
            HeroOverlayIconButtonPlain(
                icon = ChefAIIcons.Timer,
                contentDescription = stringResource(R.string.start_step_timer_content_description),
                onClick = { onStartTimer(durationSeconds) },
            )
        }
    }
}

/** A borderless icon button matching this row's tap target, without [HeroOverlayIconButton]'s
 * ground-fill-and-border chrome (there is no photo behind it here to contrast against). */
@Composable
private fun HeroOverlayIconButtonPlain(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .flatClickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RecipeDetailsFullScreenPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe, isBookmarked = false)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RecipeDetailsBookmarkedPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe, isBookmarked = true)
    }
}

@Preview(name = "From meal plan — to cook", showBackground = true)
@Preview(
    name = "From meal plan — to cook, dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RecipeDetailsCookedToggleToCookPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe, showCookedToggle = true, isCooked = false)
    }
}

@Preview(name = "From meal plan — cooked", showBackground = true)
@Preview(
    name = "From meal plan — cooked, dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RecipeDetailsCookedToggleCookedPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe, showCookedToggle = true, isCooked = true)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RecipeDetailsDeleteConfirmationPreview() {
    ChefAITheme {
        RecipeDetailsContent(
            recipe = RecipeData.recipe,
            canDelete = true,
            showDeleteConfirmation = true,
        )
    }
}

@Preview(name = "Scaled to 8 portions", showBackground = true)
@Preview(
    name = "Scaled to 8 portions — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RecipeDetailsScaledPreview() {
    ChefAITheme {
        RecipeDetailsContent(
            recipe = RecipeData.recipe,
            servings = ServingsUiState.forRecipeServings(RecipeData.recipe.servings).copy(current = 8),
        )
    }
}

@Preview(name = "Recipe with no published yield", showBackground = true)
@Preview(
    name = "Recipe with no published yield — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun RecipeDetailsEstimatedServingsPreview() {
    ChefAITheme {
        RecipeDetailsContent(recipe = RecipeData.recipe.copy(servings = 0))
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RecipeDetailsPrivatePreview() {
    ChefAITheme {
        RecipeDetailsContent(
            recipe = RecipeData.recipe.copy(privacy = RecipePrivacy.PRIVATE),
            isBookmarked = false,
        )
    }
}

private val stepsListPreviewSteps = listOf(
    RecipeStep(java.util.UUID.randomUUID(), 0, "Preheat the oven to 220°C."),
    RecipeStep(java.util.UUID.randomUUID(), 1, "Bake for 30 minutes, until golden brown."),
    RecipeStep(java.util.UUID.randomUUID(), 2, "Let rest for 5 minutes before serving."),
)

@Preview(name = "Steps tab", showBackground = true)
@Preview(
    name = "Steps tab — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun StepsListPreview() {
    ChefAITheme {
        StepsList(steps = stepsListPreviewSteps)
    }
}
