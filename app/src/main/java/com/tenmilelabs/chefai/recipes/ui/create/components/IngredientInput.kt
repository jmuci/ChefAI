package com.tenmilelabs.chefai.recipes.ui.create.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun IngredientInput(
    ingredientInput: String,
    onIngredientInputChange: (String) -> Unit = {},
    ingredientQuantity: String,
    onIngredientQuantityChange: (String) -> Unit = {},
    ingredientUnit: String,
    onIngredientUnitChange: (String) -> Unit = {},
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {},
    onAddIngredient: () -> Unit = {}
) {
    var expandedSuggestions by rememberSaveable { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = ingredientQuantity,
                onValueChange = onIngredientQuantityChange,
                label = { Text(stringResource(R.string.label_quantity)) },
                modifier = Modifier.weight(0.3f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = ingredientUnit,
                onValueChange = onIngredientUnitChange,
                label = { Text(stringResource(R.string.label_unit)) },
                placeholder = { Text(stringResource(R.string.placeholder_unit)) },
                modifier = Modifier.weight(0.35f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = expandedSuggestions && suggestions.isNotEmpty(),
                onExpandedChange = { expandedSuggestions = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = ingredientInput,
                    onValueChange = {
                        onIngredientInputChange(it)
                        expandedSuggestions = true
                    },
                    label = { Text(stringResource(R.string.label_ingredient)) },
                    placeholder = { Text(stringResource(R.string.placeholder_search)) },
                    modifier = Modifier
                        .menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                        .fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = onAddIngredient,
                            enabled = ingredientInput.isNotBlank() && ingredientQuantity.isNotBlank()
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.content_description_add_ingredient)
                            )
                        }
                    },
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (ingredientInput.isNotBlank() && ingredientQuantity.isNotBlank()) {
                                onAddIngredient()
                            }
                        }
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = expandedSuggestions && suggestions.isNotEmpty(),
                    onDismissRequest = { expandedSuggestions = false }
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                onSuggestionClick(suggestion)
                                expandedSuggestions = false
                            }
                        )
                    }
                }
            }
        }
    }
}
