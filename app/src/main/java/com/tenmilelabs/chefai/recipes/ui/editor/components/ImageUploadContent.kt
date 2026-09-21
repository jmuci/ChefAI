package com.tenmilelabs.chefai.recipes.ui.editor.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.components.flat.RowRule
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.recipeImageModel
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The photo slot — screen 19: a full-width 16:9 ground with an image icon, "Recipe photo" and a
 * "browse files" link when empty; the picked or scraped image filling it otherwise, with a small
 * remove control pinned to the top-right corner.
 *
 * The handoff draws only this slot; the image-URL alternative beneath it is existing, preserved
 * functionality (importing a hero photo by link rather than a device picker) rather than something
 * screen 19 depicts, so it keeps its own [FlatField] under an "or" label instead of appearing in
 * the empty-state copy above.
 */
@Composable
fun ImageUploadContent(
    localImagePath: String? = null,
    imageUrl: String = "",
    onImageUrlChange: (String) -> Unit = {},
    onSelectImage: () -> Unit = {},
    onClearImage: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val previewModel = recipeImageModel(localImagePath, imageUrl)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = MaterialTheme.chefColors.sectionRuleWidth,
                    color = MaterialTheme.colorScheme.outline,
                )
                .then(
                    if (previewModel == null) {
                        Modifier.flatClickable(onClick = onSelectImage, role = Role.Button)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (previewModel != null) {
                AsyncImage(
                    model = previewModel,
                    contentDescription = stringResource(R.string.image_selected),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_img_placeholder),
                    error = painterResource(R.drawable.ic_img_error),
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(RemoveButtonSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha))
                        .flatClickable(
                            onClick = {
                                onClearImage()
                                onImageUrlChange("")
                            },
                            role = Role.Button,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(ChefAIIcons.X),
                        contentDescription = stringResource(R.string.content_description_remove_image),
                        // The ground color, not literal white — legible on any photo in either theme.
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(ChefAIIcons.Image),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(PlaceholderIconSize),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.image_upload_placeholder_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row {
                        Text(
                            text = stringResource(R.string.divider_or) + " ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.image_upload_browse_files),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.chefColors.accentText,
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            RowRule(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.divider_or),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            RowRule(modifier = Modifier.weight(1f))
        }

        FlatField(
            value = imageUrl,
            onValueChange = onImageUrlChange,
            label = stringResource(R.string.label_image_url),
            placeholder = stringResource(R.string.placeholder_image_url),
            leadingIcon = ChefAIIcons.Link,
        )
    }
}

/** `styles.css`'s empty-slot glyph, well above the 20dp Lucide default — it is the slot's whole content. */
private val PlaceholderIconSize = 32.dp
private val RemoveButtonSize = 28.dp

/** `--shadow-lg`'s own alpha, reused here for the same "readable over any photo" reason. */
private const val ScrimAlpha = 0.55f

@Preview(name = "Empty", showBackground = true, showSystemUi = true)
@Preview(name = "Empty — dark", showBackground = true, showSystemUi = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ImageUploaderPreview() {
    ChefAITheme {
        ImageUploadContent(localImagePath = null)
    }
}
