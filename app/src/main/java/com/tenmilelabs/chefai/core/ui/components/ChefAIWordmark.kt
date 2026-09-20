package com.tenmilelabs.chefai.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.em
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The accent wordmark that stands in for a logo on the auth screens — "ChefAI" set as a small
 * identity kicker rather than the 120dp chef-hat drawable it replaces.
 *
 * 13px/800 uppercase, `.1em` tracking: close to `titleSmall` (13px/800, `.08em`) but the handoff
 * specifies `.1em` exactly, so the tracking is overridden here rather than reused as-is.
 *
 * [chefColors.accentText], not `colorScheme.primary` — accent-600 fails AA under 14px, the same
 * reasoning as [com.tenmilelabs.chefai.core.ui.components.RecipePrivacyBadge].
 *
 * Log in (07) shows it inline above the greeting; Create account (08) carries it in the header via
 * `ChefAITopAppBarWithWordmark`.
 */
@Composable
fun ChefAIWordmark(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.1.em),
        color = MaterialTheme.chefColors.accentText,
        modifier = modifier,
    )
}
