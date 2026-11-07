package com.tenmilelabs.chefai.mealplans.ui.create.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.mealplans.ui.create.WizardAction

/**
 * "Save this meal plan for the future?" — a toggle that, when on, reveals a required title field
 * and an optional photo. Stateless: the photo picker is launched by the caller in response to
 * [WizardAction.RequestSavedImagePick].
 */
@Composable
fun SaveForLaterSection(
    saveForLater: Boolean,
    title: String,
    imagePath: String?,
    isImportingImage: Boolean,
    onAction: (WizardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ToggleOptionRow(
            title = stringResource(R.string.wizard_save_for_later_title),
            subtitle = stringResource(R.string.wizard_save_for_later_subtitle),
            checked = saveForLater,
            onCheckedChange = { onAction(WizardAction.SetSaveForLater(it)) },
        )
        AnimatedVisibility(
            visible = saveForLater,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // The error only appears once the user has typed (or cleared) something.
                var touched by rememberSaveable { mutableStateOf(false) }
                FlatField(
                    value = title,
                    onValueChange = {
                        touched = true
                        onAction(WizardAction.SetSavedTitle(it))
                    },
                    label = stringResource(R.string.wizard_saved_title_label),
                    errorText = if (touched && title.isBlank()) {
                        stringResource(R.string.wizard_saved_title_required)
                    } else {
                        null
                    },
                )
                SavedPhotoRow(
                    imagePath = imagePath,
                    isImporting = isImportingImage,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun SavedPhotoRow(
    imagePath: String?,
    isImporting: Boolean,
    onAction: (WizardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (imagePath == null) {
        val ruleColor = MaterialTheme.colorScheme.outline
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .drawBehind {
                    drawRect(
                        color = ruleColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                        ),
                    )
                }
                .clickable(enabled = !isImporting) { onAction(WizardAction.RequestSavedImagePick) },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(ChefAIIcons.ImagePlus),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Text(
                    text = stringResource(R.string.wizard_saved_photo_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data("file://$imagePath").build(),
                placeholder = painterResource(R.drawable.ic_img_placeholder),
                error = painterResource(R.drawable.ic_img_error),
                contentDescription = stringResource(R.string.wizard_saved_photo_content_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outline),
            )
            FlatButton(
                text = stringResource(R.string.wizard_saved_photo_change),
                onClick = { onAction(WizardAction.RequestSavedImagePick) },
                variant = FlatButtonVariant.Secondary,
                enabled = !isImporting,
            )
            FlatButton(
                text = stringResource(R.string.wizard_saved_photo_remove),
                onClick = { onAction(WizardAction.RemoveSavedImage) },
                variant = FlatButtonVariant.Ghost,
                enabled = !isImporting,
            )
        }
    }
}

@Composable
private fun SaveForLaterPreviewContent(
    saveForLater: Boolean,
    title: String = "",
    imagePath: String? = null,
) {
    ChefAITheme {
        SaveForLaterSection(
            saveForLater = saveForLater,
            title = title,
            imagePath = imagePath,
            isImportingImage = false,
            onAction = {},
        )
    }
}

@Preview(name = "Save for later, off — light")
@Preview(name = "Save for later, off — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SaveForLaterOffPreview() = SaveForLaterPreviewContent(saveForLater = false)

@Preview(name = "Save for later, on empty — light")
@Preview(name = "Save for later, on empty — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SaveForLaterOnEmptyPreview() = SaveForLaterPreviewContent(saveForLater = true)

@Preview(name = "Save for later, with photo — light")
@Preview(name = "Save for later, with photo — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SaveForLaterWithPhotoPreview() = SaveForLaterPreviewContent(
    saveForLater = true,
    title = "Weeknight favourites",
    imagePath = "/data/preview/hero.jpg",
)
