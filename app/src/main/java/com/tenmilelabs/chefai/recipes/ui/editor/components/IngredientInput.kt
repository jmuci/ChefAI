package com.tenmilelabs.chefai.recipes.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatField
import com.tenmilelabs.chefai.core.ui.components.flat.FlatSuggestionsPanel
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons

/**
 * One saved ingredient — screen 19's row: a 16dp drag handle, a 42dp quantity column, a 44dp unit
 * column, the name flexing to fill the rest, and a trash icon. [dragHandle] comes from
 * [com.tenmilelabs.chefai.core.ui.components.flat.DragReorderColumn] — it is the only part of the
 * row that starts a drag.
 *
 * The design's "35%"/"45% ink" read as this system's single muted-ink role
 * (`colorScheme.onSurfaceVariant`), the same translation § 7 of the design doc makes for every
 * other percentage-of-ink value in the handoff — see `docs/design/modernist.md` § Dark-mode
 * discipline on why a literal alpha over ink is never the right tool here.
 */
@Composable
internal fun IngredientRow(
    quantityText: String,
    unit: String,
    name: String,
    dragHandle: Modifier,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = dragHandle.size(HandleTouchSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(ChefAIIcons.GripVertical),
                contentDescription = stringResource(R.string.content_description_drag_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(HandleIconSize),
            )
        }
        Text(
            text = quantityText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(QuantityColumnWidth),
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(UnitColumnWidth),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(HandleTouchSize)
                .flatClickable(onClick = onDelete, role = Role.Button),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(ChefAIIcons.Trash),
                contentDescription = stringResource(R.string.content_description_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(TrashIconSize),
            )
        }
    }
}

/**
 * The "add a new ingredient" form beneath the ingredient list: quantity, unit and an
 * autocompleting name field, one row.
 */
@Composable
internal fun IngredientAddForm(
    ingredientInput: String,
    onIngredientInputChange: (String) -> Unit,
    ingredientQuantity: String,
    onIngredientQuantityChange: (String) -> Unit,
    ingredientUnit: String,
    onIngredientUnitChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onAddIngredient: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlatField(
                value = ingredientQuantity,
                onValueChange = onIngredientQuantityChange,
                label = stringResource(R.string.label_quantity),
                modifier = Modifier.weight(0.28f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
            )
            FlatField(
                value = ingredientUnit,
                onValueChange = onIngredientUnitChange,
                label = stringResource(R.string.label_unit),
                placeholder = stringResource(R.string.placeholder_unit),
                modifier = Modifier.weight(0.32f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            FlatField(
                value = ingredientInput,
                onValueChange = onIngredientInputChange,
                label = stringResource(R.string.label_ingredient),
                placeholder = stringResource(R.string.placeholder_search),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (ingredientInput.isNotBlank() && ingredientQuantity.isNotBlank()) {
                            onAddIngredient()
                        }
                    },
                ),
            )
        }
        FlatSuggestionsPanel(
            suggestions = suggestions,
            onSuggestionClick = onSuggestionClick,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val RowMinHeight = 44.dp
private val QuantityColumnWidth = 42.dp
private val UnitColumnWidth = 44.dp

/** The design's 16dp glyph, given a larger touch target — mirrors `StepCard`'s 32dp icon buttons. */
private val HandleIconSize = 16.dp
private val TrashIconSize = 16.dp
private val HandleTouchSize = 32.dp
