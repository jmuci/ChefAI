package com.tenmilelabs.chefai.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * Spacing for paneled screens (docs/design/modernist.md § 3.1). Panels sit [Inset] in from the
 * screen edges on a `chefColors.panelGround` page, [Gap] apart.
 */
object PanelMetrics {
    /** Page margin around the panels. */
    val Inset: Dp = 12.dp

    /** Vertical gap between two panels. */
    val Gap: Dp = 12.dp

    /** Inside a panel: top/bottom padding. */
    val VerticalPadding: Dp = 16.dp

    /** Inside a panel: the gap between its header and its content. */
    val ContentGap: Dp = 12.dp
}

/**
 * One section's surface: `chefColors.panelSurface` with `chefColors.panelRadius` corners. Sections
 * separate by color and space, not rules. Content supplies its own horizontal padding — headers and
 * carousels want different insets.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    contentGap: Dp = PanelMetrics.ContentGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaterialTheme.chefColors.panelRadius))
            .background(MaterialTheme.chefColors.panelSurface)
            .padding(vertical = PanelMetrics.VerticalPadding),
        verticalArrangement = Arrangement.spacedBy(contentGap),
        content = content,
    )
}

/**
 * Paints one item of a lazy list as a slice of a single panel, for lists too long to compose
 * inside one [Panel]: the [isFirst] item takes the top corners and the [isLast] item the bottom
 * ones, so consecutive items read as one surface.
 */
@Composable
fun Modifier.panelSegment(isFirst: Boolean, isLast: Boolean): Modifier {
    val radius = MaterialTheme.chefColors.panelRadius
    val shape: Shape = RoundedCornerShape(
        topStart = if (isFirst) radius else 0.dp,
        topEnd = if (isFirst) radius else 0.dp,
        bottomStart = if (isLast) radius else 0.dp,
        bottomEnd = if (isLast) radius else 0.dp,
    )
    return this
        .clip(shape)
        .background(MaterialTheme.chefColors.panelSurface)
}

@Composable
private fun PanelPreviewContent() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.chefColors.panelGround)
            .padding(PanelMetrics.Inset),
    ) {
        Panel {
            SectionHeaderWithSubtitle(title = "Quick dinners", subtitle = "Under 30 minutes")
            Text(
                text = "Panel content",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Preview(name = "Panel — Light")
@Composable
private fun PanelPreview() {
    ChefAITheme { PanelPreviewContent() }
}

@Preview(name = "Panel — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PanelDarkPreview() {
    ChefAITheme(darkTheme = true) { PanelPreviewContent() }
}
