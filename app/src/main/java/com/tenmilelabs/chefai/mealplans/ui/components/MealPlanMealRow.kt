package com.tenmilelabs.chefai.mealplans.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.domain.model.MealSlot
import java.util.UUID

/** How much of its normal opacity a cooked row keeps. */
private const val COOKED_ALPHA = 0.55f

/**
 * One planned meal in a meal plan: thumbnail, title, and a chef-hat toggle that marks it cooked.
 *
 * Deliberately compact rather than a hero card — a week holds up to fourteen of these, and the
 * screen's job is letting the user scan what is left to cook, not browse imagery.
 *
 * @param recipe the recipe filling the slot, or `null` when it is not on this device.
 * @param slotLabel "Lunch"/"Dinner", or `null` for a plan with a single meal per day, where the
 *   label would be the same on every row and only adds noise.
 * @param isCooked drives the dimmed, struck-through treatment and the toggle's state.
 */
@Composable
fun MealPlanMealRow(
    recipe: RecipePreview?,
    isCooked: Boolean,
    onClick: () -> Unit,
    onToggleCooked: () -> Unit,
    modifier: Modifier = Modifier,
    slotLabel: String? = null,
    dayLabel: String? = null,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (isCooked) COOKED_ALPHA else 1f,
        label = "cookedAlpha",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isCooked) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "cookedContainer",
    )

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCooked) 0.dp else 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumbnail(
                recipe = recipe,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(contentAlpha),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(contentAlpha),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val caption = listOfNotNull(dayLabel, slotLabel).joinToString(" · ")
                if (caption.isNotEmpty()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    text = recipe?.title ?: stringResource(R.string.meal_plan_recipe_unavailable),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (recipe == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (isCooked) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (recipe != null) {
                    Text(
                        text = recipe.metaLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CookedToggle(isCooked = isCooked, onToggle = onToggleCooked)
        }
    }
}

/**
 * The chef-hat toggle. Filled once the meal is cooked, so a glance down the list reads as a column
 * of ticked-off hats.
 */
@Composable
private fun CookedToggle(
    isCooked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconToggleButton(
        checked = isCooked,
        onCheckedChange = { onToggle() },
        modifier = modifier.semantics { role = Role.Checkbox },
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chef_hat_black_24dp),
            contentDescription = stringResource(
                if (isCooked) R.string.meal_plan_mark_not_cooked else R.string.meal_plan_mark_cooked
            ),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun Thumbnail(
    recipe: RecipePreview?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(recipe?.let { recipeImageModel(it.localImagePath, it.imageUrlThumbnail) })
                .crossfade(true)
                .build(),
            placeholder = painterResource(R.drawable.ic_img_placeholder),
            error = painterResource(R.drawable.ic_img_placeholder),
            fallback = painterResource(R.drawable.ic_img_placeholder),
            contentDescription = stringResource(R.string.recipe_image_content_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** "25 min · Serves 4", dropping either half when the recipe does not carry it. */
private fun RecipePreview.metaLine(): String {
    val totalMinutes = prepTimeMinutes + cookTimeMinutes
    return listOfNotNull(
        totalMinutes.takeIf { it > 0 }?.let { "$it min" },
        servings.takeIf { it > 0 }?.let { "Serves $it" },
    ).joinToString(" · ")
}

private fun previewRecipe(title: String) = RecipePreview(
    uuid = UUID.randomUUID(),
    title = title,
    description = "",
    imageUrlThumbnail = "",
    prepTimeMinutes = 10,
    cookTimeMinutes = 20,
    servings = 4,
    creatorId = UUID.randomUUID(),
    tags = emptyList(),
    labels = emptyList(),
)

@Preview(name = "To cook — Light", showBackground = true)
@Composable
private fun MealPlanMealRowToCookPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanMealRow(
            recipe = previewRecipe("Spaghetti Carbonara"),
            isCooked = false,
            slotLabel = MealSlot.DINNER.label,
            onClick = {},
            onToggleCooked = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Cooked — Light", showBackground = true)
@Composable
private fun MealPlanMealRowCookedPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanMealRow(
            recipe = previewRecipe("Sheet-pan Harissa Chicken"),
            isCooked = true,
            dayLabel = "Day 2",
            slotLabel = MealSlot.LUNCH.label,
            onClick = {},
            onToggleCooked = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Cooked — Dark", showBackground = true)
@Composable
private fun MealPlanMealRowCookedDarkPreview() {
    ChefAITheme(darkTheme = true) {
        MealPlanMealRow(
            recipe = previewRecipe("Miso Aubergine Rice Bowl"),
            isCooked = true,
            dayLabel = "Day 5",
            onClick = {},
            onToggleCooked = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Recipe missing — Light", showBackground = true)
@Composable
private fun MealPlanMealRowMissingPreview() {
    ChefAITheme(darkTheme = false) {
        MealPlanMealRow(
            recipe = null,
            isCooked = false,
            slotLabel = MealSlot.DINNER.label,
            onClick = {},
            onToggleCooked = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
