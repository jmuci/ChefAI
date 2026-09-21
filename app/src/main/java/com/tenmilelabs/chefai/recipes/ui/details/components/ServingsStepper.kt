package com.tenmilelabs.chefai.recipes.ui.details.components

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.DISABLED_ALPHA
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * Test tags for the stepper's controls. `internal` on purpose — they are scaffolding for this
 * module's own tests (both source sets can see them), not part of the app's API surface.
 */
internal const val DECREASE_SERVINGS_TAG = "DecreaseServingsButton"
internal const val INCREASE_SERVINGS_TAG = "IncreaseServingsButton"
internal const val SERVINGS_COUNT_TAG = "ServingsCount"

/**
 * The `−` / count / `+` row — the Servings column of the recipe-detail stats bar (05). Stateless:
 * the count comes in and every press goes back out as the value the caller should move to. The
 * buttons disable at the ends of [range], so the callback is never asked to move outside it.
 *
 * No label of its own — the stats bar draws "SERVINGS" above all three of its columns alike, so
 * this stays just the control. The bare number is the visible label; a TalkBack user still gets
 * "N portions" via [pluralStringResource] on the count's own semantics.
 */
@Composable
fun ServingsStepper(
    servings: Int,
    range: IntRange,
    onServingsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val countDescription = pluralStringResource(R.plurals.portions_count, servings, servings)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            icon = ChefAIIcons.Minus,
            contentDescription = stringResource(R.string.decrease_portions_content_description),
            onClick = { onServingsChange(servings - 1) },
            enabled = servings > range.first,
            testTag = DECREASE_SERVINGS_TAG,
        )

        Text(
            text = servings.toString(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .size(width = ServingsCountWidth, height = StepButtonSize)
                .testTag(SERVINGS_COUNT_TAG)
                // Focus stays on the button that was pressed, so without this the new count is
                // never spoken and a TalkBack user has no confirmation the press registered.
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = countDescription
                },
        )

        StepButton(
            icon = ChefAIIcons.Plus,
            contentDescription = stringResource(R.string.increase_portions_content_description),
            onClick = { onServingsChange(servings + 1) },
            enabled = servings < range.last,
            testTag = INCREASE_SERVINGS_TAG,
        )
    }
}

@Composable
private fun StepButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .size(StepButtonSize)
            .flatClickable(onClick = onClick, enabled = enabled, role = Role.Button)
            .border(
                width = MaterialTheme.chefColors.sectionRuleWidth,
                color = MaterialTheme.colorScheme.outline,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(StepIconSize),
        )
    }
}

/** Square − / + buttons, small enough that the stepper fits a third of the stats bar's width. */
private val StepButtonSize = 32.dp
private val StepIconSize = 14.dp

/** Wide enough for two digits without the buttons shifting apart as the count changes. */
private val ServingsCountWidth = 36.dp

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
