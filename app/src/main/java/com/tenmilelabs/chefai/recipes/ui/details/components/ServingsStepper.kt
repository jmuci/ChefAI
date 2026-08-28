package com.tenmilelabs.chefai.recipes.ui.details.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.recipes.domain.scaling.RecipeScaling

/**
 * Test tags for the stepper's controls. `internal` on purpose — they are scaffolding for this
 * module's own tests (both source sets can see them), not part of the app's API surface.
 */
internal const val DECREASE_SERVINGS_TAG = "DecreaseServingsButton"
internal const val INCREASE_SERVINGS_TAG = "IncreaseServingsButton"
internal const val SERVINGS_COUNT_TAG = "ServingsCount"

/**
 * Portion count with `−` / `+` controls, above the ingredients and steps tabs on the recipe
 * details screen. Stateless: the count comes in and every press goes back out as the value the
 * caller should move to. The buttons disable at the ends of [range], so the callback is never
 * asked to move outside it.
 *
 * @param isEstimated the recipe published no yield, so [servings]' starting point was assumed.
 *   Surfaced as a caption rather than hidden, since the number is a guess even though scaling from
 *   it is still proportionally correct.
 */
@Composable
fun ServingsStepper(
    servings: Int,
    range: IntRange,
    onServingsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isEstimated: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_small)),
        ) {
            Text(
                text = stringResource(R.string.portions_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            // Sized by Material rather than by hand: the default keeps the 48dp minimum touch
            // target these two get pressed against repeatedly, as CookedToggleButton does.
            FilledTonalIconButton(
                onClick = { onServingsChange(servings - 1) },
                enabled = servings > range.first,
                modifier = Modifier.testTag(DECREASE_SERVINGS_TAG),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.decrease_portions_content_description),
                )
            }

            Text(
                text = pluralStringResource(R.plurals.portions_count, servings, servings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = SERVINGS_LABEL_MIN_WIDTH)
                    .testTag(SERVINGS_COUNT_TAG)
                    // Focus stays on the button that was pressed, so without this the new count is
                    // never spoken and a TalkBack user has no confirmation the press registered.
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )

            FilledTonalIconButton(
                onClick = { onServingsChange(servings + 1) },
                enabled = servings < range.last,
                modifier = Modifier.testTag(INCREASE_SERVINGS_TAG),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.increase_portions_content_description),
                )
            }
        }

        if (isEstimated) {
            Text(
                text = stringResource(R.string.portions_estimated, RecipeScaling.DEFAULT_SERVINGS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = dimensionResource(id = R.dimen.padding_extra_small)),
            )
        }
    }
}

/** Wide enough that "10 portions" doesn't reflow the buttons as the count changes. */
private val SERVINGS_LABEL_MIN_WIDTH = 84.dp

@Preview(name = "Servings stepper", showBackground = true)
@Preview(name = "Servings stepper — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ServingsStepperPreview() {
    ChefAITheme {
        ServingsStepper(servings = 4, range = 1..10, onServingsChange = {})
    }
}

@Preview(name = "Servings stepper — at minimum", showBackground = true)
@Preview(
    name = "Servings stepper — at minimum, dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ServingsStepperAtMinimumPreview() {
    ChefAITheme {
        ServingsStepper(servings = 1, range = 1..10, onServingsChange = {})
    }
}

@Preview(name = "Servings stepper — estimated yield", showBackground = true)
@Preview(
    name = "Servings stepper — estimated yield, dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ServingsStepperEstimatedPreview() {
    ChefAITheme {
        ServingsStepper(servings = 4, range = 1..10, onServingsChange = {}, isEstimated = true)
    }
}

@Preview(name = "Servings stepper — batch recipe", showBackground = true)
@Preview(
    name = "Servings stepper — batch recipe, dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ServingsStepperBatchPreview() {
    ChefAITheme {
        ServingsStepper(servings = 24, range = 1..24, onServingsChange = {})
    }
}
