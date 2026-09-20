package com.tenmilelabs.chefai.core.ui.navigation

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.flat.FlatTag
import com.tenmilelabs.chefai.core.ui.components.flat.FlatTagTone
import com.tenmilelabs.chefai.core.ui.components.flat.SectionRule
import com.tenmilelabs.chefai.core.ui.components.flat.flatClickable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The leading control in a header, as an explicit choice rather than a nullable icon plus a
 * nullable lambda.
 *
 * Back and Close are not interchangeable in this design: back-arrow headers sit on screens you
 * came *through* (Settings, Household, the wizard steps), and the X sits on screens you came *to*
 * and will leave without a trail (Accept invite, the editor). Modelling them as one sealed axis
 * keeps that decision at the call site and out of the bar.
 */
sealed interface ChefAINavigation {
    /** No leading control — a root screen. Home, Recipes, Meal Plans. */
    data object None : ChefAINavigation

    /** `←` — return to where this screen was opened from. */
    data class Back(val onClick: () -> Unit) : ChefAINavigation

    /** `✕` — dismiss this screen outright. */
    data class Close(val onClick: () -> Unit) : ChefAINavigation
}

/** The header's own geometry. */
private object HeaderMetrics {
    /** `padding: var(--space-4)` on all four sides of every header in the design. */
    val Padding = 16.dp

    /** `gap: var(--space-3)` between the leading control and the title. */
    val NavGap = 12.dp

    /** Lucide stroke icons at 20px, on a 44dp hit target. */
    val IconSize = 20.dp
    val IconButtonSize = 44.dp

    /** `margin-top: 2px` under the title, for the subtitle line. */
    val SubtitleGap = 2.dp
}

/**
 * The Modernist header: title, a 2dp rule beneath it, and nothing else.
 *
 * Deliberately **not** `TopAppBar`/`CenterAlignedTopAppBar`. Material's bar is a tonal `Surface`
 * that composites `surfaceTint` as it elevates and centers or insets its title to its own spec.
 * This design has no elevation, no tonal container and no centering: every header is flush on the
 * ground, left-aligned, 16dp padded, and separated from the content by the same 2dp rule that
 * separates every other section in the system. Keeping Material's bar and overriding all of that
 * leaves its insets and height behind, so the header is built from primitives.
 *
 * ### The variants
 *
 * The design uses several header shapes. They are separate composables, not one function with six
 * nullable parameters, because the shape is a design decision and a call site should have to name
 * which one it means:
 *
 * | Composable | Shape | Screens |
 * | --- | --- | --- |
 * | [ChefAITopAppBar] | title, optional trailing actions | Recipes (04) |
 * | [ChefAITopAppBarWithSubtitle] | title over a muted second line | Home (01), Meal plan detail (16) |
 * | [ChefAITopAppBarWithTag] | title with a trailing status tag | Recipe editor (19) |
 * | [ChefAITopAppBarSurface] | the bare ground + padding + rule, as a slot | wizard (13–15), shopping list (17) |
 *
 * [ChefAINavigation] is orthogonal to all of them — any shape can carry a back arrow, an X, or
 * neither.
 *
 * Headers that stack extra content beneath the title row (the wizard's progress bar, the shopping
 * list's completion bar) build on [ChefAITopAppBarSurface] rather than growing a parameter here.
 */
@Composable
fun ChefAITopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: ChefAINavigation = ChefAINavigation.None,
    actions: @Composable RowScope.() -> Unit = {},
) {
    ChefAITopAppBarSurface(modifier = modifier) {
        HeaderRow(navigation = navigation, actions = actions) {
            HeaderTitle(title)
        }
    }
}

/**
 * Title over a muted second line — the date under the Home wordmark, the date range and sharing
 * attribution under a meal plan's name.
 *
 * The leading control and the title block are top-aligned rather than centered, so a two-line
 * title block does not push the back arrow off the first line.
 */
@Composable
fun ChefAITopAppBarWithSubtitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    navigation: ChefAINavigation = ChefAINavigation.None,
    actions: @Composable RowScope.() -> Unit = {},
) {
    ChefAITopAppBarSurface(modifier = modifier) {
        HeaderRow(
            navigation = navigation,
            verticalAlignment = Alignment.Top,
            actions = actions,
        ) {
            HeaderTitle(title)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = HeaderMetrics.SubtitleGap),
            )
        }
    }
}

/**
 * Title with a status tag pinned after it — "Edit recipe · Draft".
 *
 * The tag is a plain neutral-filled label, not a Material `AssistChip`: zero radius, no border, no
 * elevation. It sits between the title and [actions], so a screen can still carry a trailing
 * button.
 */
@Composable
fun ChefAITopAppBarWithTag(
    title: String,
    tag: String,
    modifier: Modifier = Modifier,
    navigation: ChefAINavigation = ChefAINavigation.None,
    actions: @Composable RowScope.() -> Unit = {},
) {
    ChefAITopAppBarSurface(modifier = modifier) {
        HeaderRow(
            navigation = navigation,
            actions = {
                // FlatTag is non-interactive by construction, which is what a status tag is. A
                // tappable one would be a FlatChip, at a different size and hit target.
                FlatTag(tag, tone = FlatTagTone.Neutral)
                actions()
            },
        ) {
            HeaderTitle(title)
        }
    }
}

/**
 * The header ground: full width, 16dp padding, a 2dp rule along the bottom, status-bar inset
 * applied above the padding.
 *
 * Exposed so that the headers which stack content under the title row — the wizard's three-segment
 * progress bar, the shopping list's completion bar — build the same shell instead of re-deriving
 * the padding and the rule. [content] is the header's interior, above the rule.
 */
@Composable
fun ChefAITopAppBarSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(HeaderMetrics.Padding),
            content = content,
        )
        SectionRule()
    }
}

/**
 * The shared title row: leading control, the title block, then the trailing actions pushed to the
 * far edge. [title] is a column so a variant can stack a subtitle under the title.
 */
@Composable
private fun HeaderRow(
    navigation: ChefAINavigation,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(HeaderMetrics.NavGap),
    ) {
        NavigationControl(navigation)
        Column(
            modifier = Modifier.weight(1f),
            content = title,
        )
        actions()
    }
}

@Composable
private fun HeaderTitle(title: String) {
    Text(
        text = title,
        // headlineLarge is the design's h4 — 20px/800, the size every screen title uses.
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The leading icon button, or nothing at all for [ChefAINavigation.None]. 20dp glyph on a 44dp hit
 * target — `flatClickable` rather than Material's `IconButton`, which would bring a round ripple.
 */
@Composable
private fun NavigationControl(navigation: ChefAINavigation) {
    val (icon, descriptionRes, onClick) = when (navigation) {
        ChefAINavigation.None -> return
        is ChefAINavigation.Back -> Triple(
            ChefAIIcons.ArrowLeft,
            R.string.header_navigate_back,
            navigation.onClick,
        )
        is ChefAINavigation.Close -> Triple(
            ChefAIIcons.X,
            R.string.header_close,
            navigation.onClick,
        )
    }

    Box(
        modifier = Modifier
            .size(HeaderMetrics.IconButtonSize)
            .flatClickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(descriptionRes),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(HeaderMetrics.IconSize),
        )
    }
}

@Preview(name = "Header — title only", showBackground = true)
@Preview(name = "Header — title only, dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ChefAITopAppBarPreview() {
    ChefAITheme {
        ChefAITopAppBar(title = "Recipes")
    }
}

@Preview(name = "Header — back + title", showBackground = true)
@Preview(name = "Header — back + title, dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ChefAITopAppBarBackPreview() {
    ChefAITheme {
        ChefAITopAppBar(
            title = "Settings",
            navigation = ChefAINavigation.Back(onClick = {}),
        )
    }
}

@Preview(name = "Header — back + title + subtitle + action", showBackground = true)
@Preview(
    name = "Header — back + title + subtitle + action, dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun ChefAITopAppBarWithSubtitlePreview() {
    ChefAITheme {
        ChefAITopAppBarWithSubtitle(
            title = "Week plan",
            subtitle = "Aug 11 – 15 · Shared by Ana Muci",
            navigation = ChefAINavigation.Back(onClick = {}),
            actions = {
                Icon(
                    painter = painterResource(ChefAIIcons.Printer),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(HeaderMetrics.IconButtonSize)
                        .padding(12.dp),
                )
            },
        )
    }
}

@Preview(name = "Header — close + title + tag", showBackground = true)
@Preview(
    name = "Header — close + title + tag, dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun ChefAITopAppBarWithTagPreview() {
    ChefAITheme {
        ChefAITopAppBarWithTag(
            title = "Edit recipe",
            tag = "Draft",
            navigation = ChefAINavigation.Close(onClick = {}),
        )
    }
}
