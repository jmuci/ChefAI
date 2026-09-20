package com.tenmilelabs.chefai.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/**
 * A small people+label chip marking a meal plan (or its shopping list) as shared with a
 * household, naming the plan's owner.
 *
 * Renders nothing when [ownerDisplayName] is null — either the plan is personal
 * (`householdId == null`), or it's shared but the owner couldn't be resolved from the cached
 * household's member list yet (e.g. still loading). The badge deliberately never shows a
 * half-formed "Shared" label with no name attached — see ADR-014.
 *
 * Styled as the design's `.tag-accent`, matching the "Owner" tag on the household screen.
 */
@Composable
fun SharedByBadge(ownerDisplayName: String?, modifier: Modifier = Modifier) {
    if (ownerDisplayName == null) return

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
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
                painter = painterResource(ChefAIIcons.Users),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 2.dp),
            )
            Text(
                text = stringResource(R.string.meal_plan_shared_by, ownerDisplayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Preview(name = "SharedByBadge — Light")
@Composable
private fun SharedByBadgePreview() {
    ChefAITheme {
        SharedByBadge(ownerDisplayName = "Chef Owner")
    }
}

@Preview(name = "SharedByBadge — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SharedByBadgeDarkPreview() {
    ChefAITheme(darkTheme = true) {
        SharedByBadge(ownerDisplayName = "Chef Owner")
    }
}
