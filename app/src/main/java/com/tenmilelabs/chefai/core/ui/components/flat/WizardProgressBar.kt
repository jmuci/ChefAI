package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The wizard's step indicator: one equal segment per step, 5dp tall with 4dp gaps, completed
 * segments in accent and the rest in neutral-200, and a caption beneath.
 *
 * It replaces the `LinearProgressIndicator` + three inline labels this screen used to carry. A
 * continuous bar says "you are 33% of the way through something"; discrete segments say "three
 * steps, you are on the first", which is what a three-step wizard actually is. The same idea
 * reappears on meal-plan detail as one segment per meal.
 *
 * ```
 * WizardProgressBar(
 *     currentStepIndex = 0,
 *     totalSteps = uiState.totalSteps,
 *     stepLabel = stringResource(R.string.wizard_step_basics),
 *     modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
 * )
 * ```
 *
 * Used on the three wizard steps (13, 14, 15).
 *
 * @param currentStepIndex **zero-based** — the first step is `0`. The caption renders it as
 *   "Step 1 of 3".
 * @param stepLabel this step's name, already stripped of the emoji the old string carried
 *   ("The Basics"). It goes after the middot in the caption.
 */
@Composable
fun WizardProgressBar(
    currentStepIndex: Int,
    totalSteps: Int,
    stepLabel: String,
    modifier: Modifier = Modifier,
) {
    if (totalSteps <= 0) return
    val current = currentStepIndex.coerceIn(0, totalSteps - 1)
    val completed = MaterialTheme.colorScheme.primary
    val remaining = MaterialTheme.chefColors.neutral.s200

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The caption below says the same thing in words; two announcements of one fact is
                // one too many.
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(SegmentGap),
        ) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(SegmentHeight)
                        .background(if (index <= current) completed else remaining),
                )
            }
        }
        Spacer(Modifier.size(CaptionGap))
        Text(
            text = stringResource(
                R.string.wizard_step_caption,
                current + 1,
                totalSteps,
                stepLabel,
            ).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val SegmentHeight = 5.dp
private val SegmentGap = 4.dp
private val CaptionGap = 8.dp

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun WizardProgressBarPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Step 1 of 3")
        WizardProgressBar(
            currentStepIndex = 0,
            totalSteps = 3,
            stepLabel = stringResource(R.string.wizard_step_basics),
        )
        PreviewStateLabel("Step 2 of 3")
        WizardProgressBar(
            currentStepIndex = 1,
            totalSteps = 3,
            stepLabel = stringResource(R.string.wizard_step_preferences),
        )
        PreviewStateLabel("Step 3 of 3")
        WizardProgressBar(
            currentStepIndex = 2,
            totalSteps = 3,
            stepLabel = stringResource(R.string.wizard_step_extras),
        )
        PreviewStateLabel("Inside a header, padded")
        WizardProgressBar(
            currentStepIndex = 1,
            totalSteps = 3,
            stepLabel = stringResource(R.string.wizard_step_preferences),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
