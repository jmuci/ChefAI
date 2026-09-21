package com.tenmilelabs.chefai.recipes.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.model.RecipeStep
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons

/**
 * One instruction — screen 19: a 26dp accent-filled square number badge, the step text at
 * 14px/1.5, and a trash icon. The design doesn't draw a grip on this row — the screenshot crops
 * before the Steps section — but reordering already existed here (the old up/down arrows this
 * replaces), so it gets the same [dragHandle] affordance [IngredientRow] does, for the same reason:
 * a row that can be reordered needs a way to start that gesture, and the ingredient row is this
 * screen's only precedent for one.
 */
@Composable
internal fun StepCard(
    stepNumber: Int,
    step: RecipeStep,
    dragHandle: Modifier,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
        Box(
            modifier = Modifier
                .size(BadgeSize)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stepNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = step.instruction,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = StepLineHeight),
            color = MaterialTheme.colorScheme.onBackground,
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
                contentDescription = stringResource(R.string.content_description_delete_step),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(HandleIconSize),
            )
        }
    }
}

/** "26dp accent-filled SQUARE number badge". */
private val BadgeSize = 26.dp

private val HandleIconSize = 16.dp
private val HandleTouchSize = 32.dp

/** "step text 14px/1.5" — 1.5x the 14sp body size. */
private val StepLineHeight = 21.sp
