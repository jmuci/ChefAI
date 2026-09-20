package com.tenmilelabs.chefai.recipes.ui.urlimport

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatBlockButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.navigation.ChefAINavigation
import com.tenmilelabs.chefai.core.ui.navigation.ChefAITopAppBar
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import java.util.UUID

@Composable
fun ImportRecipeRoute(
    onNavigateToEditorWithDraft: (UUID) -> Unit,
    onNavigateToBrowserImport: (String) -> Unit,
    onNavigateToManualEditor: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ImportRecipeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ImportEffect.NavigateToEditorWithDraft -> onNavigateToEditorWithDraft(effect.draftId)
                is ImportEffect.NavigateToBrowserImport -> onNavigateToBrowserImport(effect.url)
                ImportEffect.NavigateToManualEditor -> onNavigateToManualEditor()
                ImportEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    ImportRecipeScreen(state = state, onAction = viewModel::dispatch, onNavigateBack = onNavigateBack)
}

@Composable
fun ImportRecipeScreen(
    state: ImportRecipeState,
    onAction: (ImportAction) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val clipboard = LocalClipboard.current
    // Tracks the latest state inside the effect below — the clipboard read is async, so without
    // re-checking against the current (not the initial) url, text the user types while it's still
    // in flight would be silently overwritten once it resolves.
    val currentState = rememberUpdatedState(state)
    LaunchedEffect(Unit) {
        if (currentState.value.url.isNotBlank()) return@LaunchedEffect
        val clipboardText = runCatching {
            clipboard.getClipEntry()?.clipData?.let { data ->
                if (data.itemCount > 0) data.getItemAt(0).text?.toString() else null
            }
        }.getOrNull()
        val isHttpUrl = clipboardText != null &&
            (clipboardText.startsWith("http://") || clipboardText.startsWith("https://"))
        if (isHttpUrl && currentState.value.url.isBlank()) {
            onAction(ImportAction.UrlChanged(clipboardText))
        }
    }

    Column(
        modifier = modifier
            .testTag("ImportRecipeScreen")
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ChefAITopAppBar(
            title = stringResource(R.string.import_recipe_title),
            navigation = ChefAINavigation.Close(onClick = onNavigateBack),
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.import_recipe_lede),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlatField(
                value = state.url,
                onValueChange = { onAction(ImportAction.UrlChanged(it)) },
                label = stringResource(R.string.import_recipe_url_label),
                placeholder = stringResource(R.string.import_recipe_url_placeholder),
                leadingIcon = ChefAIIcons.Link,
                enabled = !state.isImporting,
                // Deliberately not driving FlatField's own errorText here: that ties the accent-700
                // border to a message rendered *under the field* (by design — see FlatField's own
                // docs on why there's no separate isError flag), and this screen's message already
                // has its one home in the banner below, matching the mock exactly. Passing the real
                // text through both would put it on screen twice.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (state.canImport) onAction(ImportAction.Import) }),
                modifier = Modifier.testTag("ImportUrlField"),
            )

            if (state.errorRes != null) {
                ImportErrorBanner(text = stringResource(state.errorRes))
            }

            FlatBlockButton(
                text = stringResource(R.string.import_recipe_button),
                onClick = { onAction(ImportAction.Import) },
                enabled = state.canImport,
                loading = state.isImporting,
                modifier = Modifier.testTag("ImportButton"),
            )

            if (state.errorRes != null && state.showManualEntryOption) {
                FlatBlockButton(
                    text = stringResource(R.string.import_recipe_enter_manually),
                    onClick = { onAction(ImportAction.EnterManually) },
                    variant = FlatButtonVariant.Ghost,
                )
            }
        }
    }
}

/**
 * The one tinted-fill pattern in the design: `accent-100` fill, `accent-900` icon and text — the
 * import failure is important enough to earn the accent, where every other invalid-field message in
 * the app is plain `colorScheme.error` text.
 *
 * DARK MODE FLAG: this is one of the three cases the handoff's Dark mode section says will not
 * invert mechanically — the fill has to come from the *deep* end of the accent ramp with light
 * text, not a darkened `accent-100`. `primaryContainer`/`onPrimaryContainer` are provisional in dark
 * (see `ChefColors`), so this renders as a dark tinted block rather than magenta, but nobody has
 * decided whether that provisional value is *right* for this banner specifically.
 * TODO(dark): revisit once the accent ramp itself is designed — see docs/design/modernist.md § 5.
 */
@Composable
private fun ImportErrorBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(ChefAIIcons.CircleAlert),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Preview(name = "Empty — Light", showBackground = true)
@Composable
private fun ImportRecipeScreenEmptyLightPreview() {
    ChefAITheme(darkTheme = false) {
        ImportRecipeScreen(state = ImportRecipeState(), onAction = {})
    }
}

@Preview(name = "Loading — Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ImportRecipeScreenLoadingDarkPreview() {
    ChefAITheme(darkTheme = true) {
        ImportRecipeScreen(
            state = ImportRecipeState(url = "https://example.com/recipe", isImporting = true),
            onAction = {},
        )
    }
}

@Preview(name = "Error — Light", showBackground = true)
@Composable
private fun ImportRecipeScreenErrorLightPreview() {
    ChefAITheme(darkTheme = false) {
        ImportRecipeScreen(
            state = ImportRecipeState(
                url = "https://example.com/not-a-recipe",
                errorRes = R.string.import_recipe_no_recipe_found,
                showManualEntryOption = true,
            ),
            onAction = {},
        )
    }
}

@Preview(name = "Error — Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ImportRecipeScreenErrorDarkPreview() {
    ChefAITheme(darkTheme = true) {
        ImportRecipeScreen(
            state = ImportRecipeState(
                url = "not a url",
                errorRes = R.string.import_recipe_invalid_url,
            ),
            onAction = {},
        )
    }
}
