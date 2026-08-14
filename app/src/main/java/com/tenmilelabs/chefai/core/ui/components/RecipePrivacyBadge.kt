package com.tenmilelabs.chefai.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.util.RecipePrivacy
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/**
 * A small lock+label chip marking a recipe as private.
 *
 * Renders nothing for [RecipePrivacy.PUBLIC] — a public recipe is the unremarkable case, and the
 * surfaces this appears on (recipe list cards, the details screen) already carry up to six
 * tag/label [InfoChip]s, so a chip on every row would be noise.
 *
 * A sibling of [InfoChip] rather than a third [InfoChipType]: `InfoChip` takes text only with no
 * icon slot, and its three existing call sites shouldn't grow a parameter for this.
 */
@Composable
fun RecipePrivacyBadge(privacy: RecipePrivacy, modifier: Modifier = Modifier) {
    if (privacy != RecipePrivacy.PRIVATE) return

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.padding(end = dimensionResource(id = R.dimen.padding_extra_small)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = dimensionResource(id = R.dimen.padding_small),
                vertical = dimensionResource(id = R.dimen.padding_extra_small),
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 2.dp),
            )
            Text(
                text = stringResource(R.string.privacy_private),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Preview
@Composable
private fun RecipePrivacyBadgePreview() {
    ChefAITheme {
        RecipePrivacyBadge(privacy = RecipePrivacy.PRIVATE)
    }
}

@Preview
@Composable
private fun RecipePrivacyBadgeDarkPreview() {
    ChefAITheme(darkTheme = true) {
        RecipePrivacyBadge(privacy = RecipePrivacy.PRIVATE)
    }
}
