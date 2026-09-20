package com.tenmilelabs.chefai.recipes.ui.editor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.components.flat.FlatSuggestionsPanel

/**
 * A [FlatField] with a suggestions list beneath it — "+ Add Tags" / "+ Add Labels" on the recipe
 * editor (screen 19). See [FlatSuggestionsPanel] for why the list is inline rather than a floating
 * dropdown.
 */
@Composable
public fun AutocompleteInput(
    value: String,
    onValueChange: (String) -> Unit = {},
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {},
    onEnterPressed: () -> Unit = {},
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        FlatField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEnterPressed() }),
        )
        FlatSuggestionsPanel(
            suggestions = suggestions,
            onSuggestionClick = onSuggestionClick,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
