package com.tenmilelabs.chefai.mealplans.ui.create.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.WizardProgressBar
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.navigation.ChefAITopAppBarSurface
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme

/**
 * The wizard's header: X, "New plan", the step progress beneath it.
 *
 * All three steps carry the close icon rather than a back arrow — the design's X/back distinction
 * is about the screen, not the step, and "New plan" is a flow the user came *to* and can leave
 * without a trail from any step. Stepping *back* is the footer's job.
 */
@Composable
fun WizardHeader(
    currentStepIndex: Int,
    totalSteps: Int,
    stepLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChefAITopAppBarSurface(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .flatClickable(onClick = onClose, role = Role.Button),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(ChefAIIcons.X),
                    contentDescription = stringResource(R.string.header_close),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(R.string.wizard_new_plan_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(16.dp))
        WizardProgressBar(
            currentStepIndex = currentStepIndex,
            totalSteps = totalSteps,
            stepLabel = stepLabel,
        )
    }
}

@Preview(name = "Wizard header — light")
@Preview(name = "Wizard header — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WizardHeaderPreview() {
    ChefAITheme {
        WizardHeader(
            currentStepIndex = 0,
            totalSteps = 3,
            stepLabel = "The Basics",
            onClose = {},
        )
    }
}
