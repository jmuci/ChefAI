package com.tenmilelabs.chefai.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
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
            .padding(vertical = dimensionResource(R.dimen.padding_medium)),
    ) {
        Text(
            text = stringResource(R.string.settings_measurement_units_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium)),
        )
        Text(
            text = stringResource(R.string.settings_measurement_units_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = dimensionResource(R.dimen.padding_medium),
                vertical = dimensionResource(R.dimen.padding_extra_small),
            ),
        )

        Column(Modifier.selectableGroup()) {
            MeasurementSystemOption.entries.forEach { option ->
                MeasurementSystemRow(
                    option = option,
                    isSelected = uiState.measurementSystem == option.system,
                    onSelect = { onAction(SettingsAction.MeasurementSystemChanged(option.system)) },
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = dimensionResource(R.dimen.padding_small)),
        )
        Text(
            text = stringResource(R.string.settings_measurement_units_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium)),
        )
    }
}

@Composable
private fun MeasurementSystemRow(
    option: MeasurementSystemOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(
                horizontal = dimensionResource(R.dimen.padding_medium),
                vertical = dimensionResource(R.dimen.padding_small),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Column(
            modifier = Modifier
                .padding(start = dimensionResource(R.dimen.padding_medium))
                .fillMaxWidth(),
        ) {
            Text(
                text = stringResource(option.labelRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(option.exampleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
