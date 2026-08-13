package com.tenmilelabs.chefai.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipePreview
import com.tenmilelabs.chefai.core.ui.preview.PreviewData
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import java.util.UUID

/**
 * A large, visually prominent card for displaying recipe information.
 * Designed for horizontal scrolling carousels and featured content.
 *
 * @param recipe The recipe preview data to display
 * @param modifier Modifier for customizing the card appearance and behavior. Default size is 300x220dp.
 * @param isInCollection Whether the recipe is already saved to collection (default false)
 * @param onClick Callback when the card is clicked, receives the recipe UUID
 * @param onSaveToCollection Callback when the save to collection button is clicked, receives the recipe UUID
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LargeCard(
    recipe: RecipePreview,
    modifier: Modifier = Modifier
        .width(300.dp)
        .height(220.dp),
    isInCollection: Boolean = false,
    onClick: (UUID) -> Unit = {},
    onSaveToCollection: (UUID) -> Unit = {}
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        onClick = { onClick(recipe.uuid) }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background Image
            val imageRequest = ImageRequest.Builder(LocalContext.current)
                .data(recipeImageModel(recipe.localImagePath, recipe.imageUrlThumbnail))
                .crossfade(true)
                .build()

            AsyncImage(
                model = imageRequest,
                placeholder = painterResource(R.drawable.ic_img_placeholder),
                error = painterResource(R.drawable.ic_img_error),
                contentDescription = stringResource(R.string.recipe_image_content_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient overlay for better text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 100f
                        )
                    )
            )

            // Save to Collection button (top end)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimensionResource(R.dimen.padding_small) ),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = { onSaveToCollection(recipe.uuid) },
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = if (isInCollection) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(R.string.save_to_collection_content_description),
                        tint = if (isInCollection) Color(0xFFFFD700) else Color.White
                    )
                }
            }

            // Content overlay
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val cardHeight = maxHeight
                val cardWidth = maxWidth
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top section - Labels/Tags
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxLines = 1
                    ) {
                        recipe.labels.take(2).forEach { label ->
                            InfoChip(
                                text = label.displayName,
                                type = InfoChipType.LABEL
                            )
                        }
                    }

                    // Bottom section - Recipe info
                    Column {
                        // Title
                        Text(
                            text = recipe.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Description (only show if card height >= 200dp)
                        if (cardHeight >= 180.dp || cardWidth >= 220.dp) {
                            Text(
                                text = recipe.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Metadata row (time and servings)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Time indicator
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Time",
                                    tint = Color.White,
                                    modifier = Modifier.height(16.dp)
                                )
                                Text(
                                    text = "${recipe.prepTimeMinutes + recipe.cookTimeMinutes}m",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Servings indicator
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = "Servings",
                                    tint = Color.White,
                                    modifier = Modifier.height(16.dp)
                                )
                                Text(
                                    text = "${recipe.servings} servings",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun LargeCardPreview() {
    ChefAITheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            LargeCard(recipe = PreviewData.grilledChickenRecipe)
        }
    }
}
@Preview
@Composable
private fun LargeCardInCollectionPreview() {
    ChefAITheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            LargeCard(
                recipe = PreviewData.grilledChickenRecipe,
                isInCollection = true
            )
        }
    }
}

@Preview
@Composable
private fun LargeCardDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface(modifier = Modifier.padding(16.dp)) {
            LargeCard(recipe = PreviewData.grilledChickenRecipe)
        }
    }
}
