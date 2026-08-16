package com.tenmilelabs.chefai.search.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.util.UUID

/**
 * The Search tab.
 *
 * Temporary stub — nav plumbing only, wired up so [com.tenmilelabs.chefai.core.ui.navigation.ChefAINavGraph]
 * and the bottom nav bar have somewhere to go. Step 2 of the search-tab plan
 * (`docs/prompts/search-tab-plan.md`) fills this in with the search bar moved off Home plus the
 * category-card browse page. [snackbarHostState] and [onRecipeClick] are unused for now — they are
 * part of that step's contract, so the signature is settled here rather than changing call sites
 * again later.
 */
@Composable
fun SearchScreen(
    snackbarHostState: SnackbarHostState,
    onRecipeClick: (UUID) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize())
}
