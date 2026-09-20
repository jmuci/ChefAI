package com.tenmilelabs.chefai.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.ui.components.flat.FlatRadio
import com.tenmilelabs.chefai.core.ui.components.flat.MinHitTarget
import com.tenmilelabs.chefai.core.ui.components.flat.RuledGroup
import com.tenmilelabs.chefai.core.ui.components.flat.flatSelectable
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = SettingsMetrics.ScreenVerticalPadding),
    ) {
        Text(
            // labelMedium carries the uppercase tracking but not the transform — see Type.kt.
            text = stringResource(R.string.settings_measurement_units_title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = SettingsMetrics.HorizontalPadding),
        )
        Text(
            text = stringResource(R.string.settings_measurement_units_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = SettingsMetrics.HorizontalPadding,
                vertical = SettingsMetrics.TitleToSubtitleGap,
            ),
        )

        RuledGroup(
            items = MeasurementSystemOption.entries,
            modifier = Modifier
                .padding(top = SettingsMetrics.SubtitleToGroupGap)
                .selectableGroup(),
        ) { option ->
            MeasurementSystemRow(
                option = option,
                isSelected = uiState.measurementSystem == option.system,
                onSelect = { onAction(SettingsAction.MeasurementSystemChanged(option.system)) },
            )
        }

        Text(
            text = stringResource(R.string.settings_measurement_units_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SettingsMetrics.HorizontalPadding),
        )
    }
}

/** One radio row — 44dp minimum, the selected label going 800. See [FlatRadio]'s own preview. */
@Composable
private fun MeasurementSystemRow(
    option: MeasurementSystemOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinHitTarget)
            .flatSelectable(selected = isSelected, onClick = onSelect)
            .padding(
                horizontal = SettingsMetrics.HorizontalPadding,
                vertical = SettingsMetrics.RowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsMetrics.RadioGap),
    ) {
        FlatRadio(selected = isSelected, onClick = null)
        Column {
            Text(
                text = stringResource(option.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
            )
            Text(
                text = stringResource(option.exampleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The section's own spacing, transcribed from the handoff's CSS margins (screen 10). */
private object SettingsMetrics {
    val ScreenVerticalPadding = 16.dp
    val HorizontalPadding = 16.dp

    /** `p { margin: 6px 0 var(--space-3) }` — 6px under the section label. */
    val TitleToSubtitleGap = 6.dp

    /** The subtitle's own `margin-bottom: var(--space-3)` before the ruled group. */
    val SubtitleToGroupGap = 12.dp

    /** `padding: var(--space-3) 0` inside each row. */
    val RowVerticalPadding = 12.dp
    val RadioGap = 12.dp
}

/**
 * The three choices in the order they are offered. Each carries a worked example, because
 * "Metric" on its own does not tell anyone that a cup of flour will come back as grams while a cup
 * of stock comes back as millilitres.
 */
private enum class MeasurementSystemOption(
    val system: MeasurementSystem,
    @param:StringRes val labelRes: Int,
    @param:StringRes val exampleRes: Int,
) {
    AS_WRITTEN(
        MeasurementSystem.AS_WRITTEN,
        R.string.settings_units_as_written,
        R.string.settings_units_as_written_example,
    ),
    METRIC(
        MeasurementSystem.METRIC,
        R.string.settings_units_metric,
        R.string.settings_units_metric_example,
    ),
    IMPERIAL(
        MeasurementSystem.IMPERIAL,
        R.string.settings_units_imperial,
        R.string.settings_units_imperial_example,
    ),
}

@Preview(name = "Settings – light", showBackground = true)
@Composable
private fun SettingsContentPreview() {
    ChefAITheme {
        Surface {
            SettingsContent(
                uiState = SettingsUiState(measurementSystem = MeasurementSystem.METRIC),
                onAction = {},
            )
        }
    }
}

@Preview(name = "Settings – dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsContentDarkPreview() {
    ChefAITheme {
        Surface {
            SettingsContent(
                uiState = SettingsUiState(measurementSystem = MeasurementSystem.AS_WRITTEN),
                onAction = {},
            )
        }
    }
}
