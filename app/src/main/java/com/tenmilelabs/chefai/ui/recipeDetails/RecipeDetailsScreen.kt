package com.tenmilelabs.chefai.ui.recipeDetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.domain.model.Label
import com.tenmilelabs.chefai.domain.model.Recipe
import com.tenmilelabs.chefai.domain.model.User
import com.tenmilelabs.chefai.ui.components.InfoChip
import com.tenmilelabs.chefai.ui.components.InfoChipType
import com.tenmilelabs.chefai.ui.components.RecipeTimeRow
import com.tenmilelabs.chefai.ui.preview.RecipeData
import com.tenmilelabs.chefai.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.util.EmptyContent
import com.tenmilelabs.chefai.util.LoadingContent
import timber.log.Timber
import java.util.UUID


@Composable
fun RecipeDetailsScreen(
    viewModel: RecipeDetailsViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState.isLoading) {
        LoadingContent()
    } else {
        if (uiState.recipe != null) {
            RecipeDetailsContent(uiState.recipe!!)
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
            snackbarHostState.showSnackbar(message = snackbarText,  duration = SnackbarDuration.Short)
            viewModel.snackbarMessageShown()
        }
    }
}

@Composable
fun RecipeDetailsContent(
    recipe: Recipe,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(id = R.dimen.padding_medium)),
    ) {
        Text(
            text = recipe.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        AsyncImage(
            model = recipe.imageUrl,
            placeholder = painterResource(R.drawable.ic_img_placeholder),
            error = painterResource(R.drawable.ic_img_error),
            contentDescription = stringResource(R.string.recipe_image_content_description),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .padding(vertical = dimensionResource(id = R.dimen.padding_small))
                .height(200.dp)
        )
        //TODO support multiple labels
        RecipeTimeRow(recipe.prepTimeMinutes, recipe.cookTimeMinutes)
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
        Text(
            text = stringResource(R.string.recipe_steps),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = recipe.description,
        )
        Text(
            buildAnnotatedString {
                withLink(
                    LinkAnnotation.Url(
                        recipe.recipeExternalUrl ?: "", // TODO hide field if null URL
                        TextLinkStyles(style = SpanStyle(color = Color.Blue))
                    )
                ) {
                    append(stringResource(R.string.recipe_hyperlink))
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RecipeDetailsFullScreenPreview() {
    ChefAITheme {
        RecipeDetailsContent(RecipeData.recipe)
    }
}