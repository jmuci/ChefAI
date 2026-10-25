package com.tenmilelabs.chefai.developeroptions.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatSwitch
import com.tenmilelabs.chefai.core.ui.components.flat.MinHitTarget
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.developeroptions.domain.model.ApiEnvironment

/** Debug-only. Reached from the login screen and from Settings; see [DeveloperOptionsLink]. */
@Composable
fun DeveloperOptionsScreen(
    modifier: Modifier = Modifier,
    viewModel: DeveloperOptionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DeveloperOptionsContent(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
fun DeveloperOptionsContent(
    uiState: DeveloperOptionsUiState,
    onAction: (DeveloperOptionsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = DeveloperOptionsMetrics.ScreenVerticalPadding)
            .testTag("DeveloperOptionsScreen"),
    ) {
        Text(
            // labelMedium carries the uppercase tracking but not the transform — see Type.kt.
            text = stringResource(R.string.developer_options_environment_title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = DeveloperOptionsMetrics.HorizontalPadding),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinHitTarget)
                .padding(
                    horizontal = DeveloperOptionsMetrics.HorizontalPadding,
                    vertical = DeveloperOptionsMetrics.RowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.developer_options_use_remote),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = uiState.activeBaseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(DeveloperOptionsMetrics.SwitchGap))
            FlatSwitch(
                checked = uiState.isRemote,
                onCheckedChange = { onAction(DeveloperOptionsAction.UseRemoteEnvironmentChanged(it)) },
                modifier = Modifier.testTag("UseRemoteEnvironmentSwitch"),
            )
        }

        Text(
            text = stringResource(R.string.developer_options_environment_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DeveloperOptionsMetrics.HorizontalPadding),
        )
    }
}

/** Same rhythm as the Settings screen this sits behind. */
private object DeveloperOptionsMetrics {
    val ScreenVerticalPadding = 16.dp
    val HorizontalPadding = 16.dp
    val RowVerticalPadding = 12.dp
    val SwitchGap = 16.dp
}

private val PreviewState = DeveloperOptionsUiState(
    environment = ApiEnvironment.LOCAL,
    localBaseUrl = "http://10.0.2.2:8080",
    remoteBaseUrl = "https://api.example.com",
)

@Preview(name = "Developer options – light", showBackground = true)
@Composable
private fun DeveloperOptionsContentPreview() {
    ChefAITheme {
        Surface {
            DeveloperOptionsContent(uiState = PreviewState, onAction = {})
        }
    }
}

@Preview(
    name = "Developer options – dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DeveloperOptionsContentDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface {
            DeveloperOptionsContent(
                uiState = PreviewState.copy(environment = ApiEnvironment.REMOTE),
                onAction = {},
            )
        }
    }
}
