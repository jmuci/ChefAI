package com.tenmilelabs.chefai.mealplans.ui.create.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.SectionRule
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.chefColors

@Composable
fun ServingsSelector(
    servings: Int,
    onServingsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WizardSectionLabel(stringResource(R.string.wizard_servings_title))
        SectionRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServingsStepButton(
                icon = ChefAIIcons.Minus,
                contentDescription = stringResource(R.string.wizard_servings_decrease),
                onClick = { onServingsChanged(servings - 1) },
                enabled = servings > MIN_SERVINGS,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = servings.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.wizard_servings_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            ServingsStepButton(
                icon = ChefAIIcons.Plus,
                contentDescription = stringResource(R.string.wizard_servings_increase),
                onClick = { onServingsChanged(servings + 1) },
                enabled = servings < MAX_SERVINGS,
            )
        }
        SectionRule()
    }
}

@Composable
private fun ServingsStepButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .flatClickable(onClick = onClick, enabled = enabled)
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
            modifier = Modifier.size(18.dp),
        )
    }
}

private const val MIN_SERVINGS = 1
private const val MAX_SERVINGS = 12
private const val DisabledAlpha = 0.45f
