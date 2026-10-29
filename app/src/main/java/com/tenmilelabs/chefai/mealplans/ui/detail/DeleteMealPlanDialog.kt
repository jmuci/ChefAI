package com.tenmilelabs.chefai.mealplans.ui.detail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButton
import com.tenmilelabs.chefai.core.ui.components.flat.FlatButtonVariant
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.recipes.ui.editor.components.DialogPanel

/** Confirms deleting a meal plan; same panel treatment as the editor's dialogs. */
@Composable
fun DeleteMealPlanDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DeleteMealPlanContent(onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

/** [DeleteMealPlanDialog]'s panel without the window, so it can be previewed. */
@Composable
private fun DeleteMealPlanContent(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogPanel {
        Text(
            text = stringResource(R.string.meal_plan_delete_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.meal_plan_delete_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            FlatButton(
                text = stringResource(R.string.cancel_button),
                onClick = onDismiss,
                variant = FlatButtonVariant.Ghost,
            )
            FlatButton(
                text = stringResource(R.string.meal_plan_delete_confirm),
                onClick = onConfirm,
                variant = FlatButtonVariant.Destructive,
                modifier = Modifier.testTag("DeleteMealPlanConfirm"),
            )
        }
    }
}

@Preview(name = "Delete meal plan – light", showBackground = true)
@Composable
private fun DeleteMealPlanPreview() {
    ChefAITheme {
        Surface { DeleteMealPlanContent(onConfirm = {}, onDismiss = {}) }
    }
}

@Preview(name = "Delete meal plan – dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeleteMealPlanDarkPreview() {
    ChefAITheme {
        Surface { DeleteMealPlanContent(onConfirm = {}, onDismiss = {}) }
    }
}
