package com.tenmilelabs.chefai.recipes.ui.urlimport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import java.util.UUID

@Composable
fun ImportRecipeRoute(
    onNavigateToEditorWithDraft: (UUID) -> Unit,
    onNavigateToManualEditor: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ImportRecipeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ImportEffect.NavigateToEditorWithDraft -> onNavigateToEditorWithDraft(effect.draftId)
                ImportEffect.NavigateToManualEditor -> onNavigateToManualEditor()
                ImportEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    ImportRecipeScreen(state = state, onAction = viewModel::dispatch)
}

@Composable
fun ImportRecipeScreen(
    state: ImportRecipeState,
    onAction: (ImportAction) -> Unit,
    modifier: Modifier = Modifier,
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
            .padding(dimensionResource(R.dimen.padding_medium)),
    ) {
        Text(stringResource(R.string.import_recipe_title), style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(dimensionResource(R.dimen.padding_large)))

        OutlinedTextField(
            value = state.url,
            onValueChange = { onAction(ImportAction.UrlChanged(it)) },
            label = { Text(stringResource(R.string.import_recipe_url_label)) },
            placeholder = { Text(stringResource(R.string.import_recipe_url_placeholder)) },
            singleLine = true,
            enabled = !state.isImporting,
            isError = state.errorRes != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (state.canImport) onAction(ImportAction.Import) }),
            modifier = Modifier.fillMaxWidth().testTag("ImportUrlField"),
        )

        Spacer(Modifier.height(dimensionResource(R.dimen.padding_medium)))

        Button(
            onClick = { onAction(ImportAction.Import) },
            enabled = state.canImport,
            modifier = Modifier.fillMaxWidth().testTag("ImportButton"),
        ) {
            if (state.isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.import_recipe_button))
            }
        }

        state.errorRes?.let { errorRes ->
            Spacer(Modifier.height(dimensionResource(R.dimen.padding_medium)))
            Text(stringResource(errorRes), color = MaterialTheme.colorScheme.error)

            if (state.showManualEntryOption) {
                TextButton(onClick = { onAction(ImportAction.EnterManually) }) {
                    Text(stringResource(R.string.import_recipe_enter_manually))
                }
            }
        }
    }
}

@Preview(name = "Empty — Light", showBackground = true)
@Composable
private fun ImportRecipeScreenEmptyLightPreview() {
    ChefAITheme(darkTheme = false) {
        ImportRecipeScreen(state = ImportRecipeState(), onAction = {})
    }
}

@Preview(name = "Loading — Dark", showBackground = true)
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

@Preview(name = "Error — Dark", showBackground = true)
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
