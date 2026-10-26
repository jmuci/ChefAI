package com.tenmilelabs.chefai.core.ui.navigation

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tenmilelabs.chefai.core.ui.components.flat.flatSelectable
import com.tenmilelabs.chefai.core.ui.icons.ChefAIIcons
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * Destinations used in the [ChefAIApp] Bottom Nav Bar.
 *
 * Four tabs, in this order: Home / Search / Recipes / Meal Plans. The order is the design's and is
 * load-bearing — Search sits second, not last.
 */
enum class TopLevelDestination(
    @param:DrawableRes val icon: Int,
    val appDestination: AppDestinations,
) {
    HOME(icon = ChefAIIcons.House, appDestination = AppDestinations.HOME),
    SEARCH(icon = ChefAIIcons.Search, appDestination = AppDestinations.SEARCH),
    RECIPES(icon = ChefAIIcons.BookOpen, appDestination = AppDestinations.RECIPES),
    MEAL_PLANS(icon = ChefAIIcons.ChefHat, appDestination = AppDestinations.MEAL_PLANS),
}

/**
 * The nav bar's own geometry. Small enough to be obvious, specific enough that a screen should not
 * re-derive it.
 */
private object NavBarMetrics {
    /** Lucide stroke icons at the design's 20px. */
    val IconSize = 20.dp

    /** Vertical padding on each item. The indicator pill adds its own 4dp above and below. */
    val ItemTopPadding = 6.dp
    val ItemBottomPadding = 8.dp

    /** The active-tab pill behind the icon: 4dp above/below the 20dp icon, 20dp either side. */
    val IndicatorVerticalPadding = 4.dp
    val IndicatorHorizontalPadding = 20.dp

    /** How far above the bar its shade reaches into the content. */
    val ShadeHeight = 16.dp

    /** `gap: 4px` between icon and label. */
    val IconLabelGap = 4.dp

    /**
     * The nav labels are **not** the uppercase `labelSmall` the type scale documents. They are
     * 10px/800 in *title case* with tight tracking (`letter-spacing: -.01em`) — the design's own
     * markup, and what the screenshots show. So the size and weight come from `labelSmall` and the
     * tracking is overridden here; that is the whole deviation.
     */
    val LabelTracking = (-0.01).em

    /** The rail's fixed width — the bar's item width, carried over so the two read as one control. */
    val RailWidth = 88.dp
}

/**
 * The bottom nav: a panel-toned bar that casts a soft shade upward onto the content instead of
 * sitting under a rule, with a tinted pill behind the active tab.
 *
 * Deliberately **not** `NavigationBar`/`NavigationBarItem`. Material's bar resolves its own
 * container tone through tonal elevation and its item brings a ripple and its own indicator
 * metrics; this one takes every color from [chefColors] (navSurface, navIndicator,
 * navActiveContent, navShade) so dark can retheme it in one place. So the row is built out of
 * primitives.
 *
 * The shade is drawn *outside* the bar's bounds, over the bottom of the content — a `shadow()`
 * modifier would not do: Android casts elevation shadows downward, so a bottom bar's is all but
 * invisible. In dark [chefColors].navShade is transparent and the bar separates by tone alone.
 * See docs/design/modernist.md § 9.2.
 */
@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val selectedTab = rememberSelectedTab(navController)

    val shade = MaterialTheme.chefColors.navShade

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val shadeHeight = NavBarMetrics.ShadeHeight.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(shade.copy(alpha = 0f), shade),
                        startY = -shadeHeight,
                        endY = 0f,
                    ),
                    topLeft = Offset(0f, -shadeHeight),
                    size = Size(size.width, shadeHeight),
                )
            }
            .background(MaterialTheme.chefColors.navSurface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                NavItem(
                    destination = destination,
                    selected = destination == selectedTab,
                    onClick = { navController.selectTab(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The same bar turned on its side for expanded widths. Same surface, same items, no rule — a rail
 * is a layout change, not a different component. It casts no shade: beside the content rather
 * than over it, the tone change alone separates them.
 */
@Composable
fun NavigationRailBar(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val selectedTab = rememberSelectedTab(navController)

    Column(
        modifier = modifier
            .width(NavBarMetrics.RailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.chefColors.navSurface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TopLevelDestination.entries.forEach { destination ->
            NavItem(
                destination = destination,
                selected = destination == selectedTab,
                onClick = { navController.selectTab(destination) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One tab. Icon over label; the active one takes [chefColors].navActiveContent and sits its icon
 * on a [chefColors].navIndicator pill. The pill is always laid out — transparent when inactive —
 * so switching tabs never shifts the icon.
 */
@Composable
private fun NavItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.appDestination.title)
    val contentColor = if (selected) {
        MaterialTheme.chefColors.navActiveContent
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val indicatorColor = if (selected) MaterialTheme.chefColors.navIndicator else Color.Unspecified

    Column(
        modifier = modifier
            // flatSelectable, not flatClickable: a tab *has* a state, and in this design the only
            // signal for it is color — a screen reader gets nothing unless it is in the semantics.
            // It also brings the system's flat pressed tint and focus ring in place of a ripple.
            .flatSelectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(
                top = NavBarMetrics.ItemTopPadding,
                bottom = NavBarMetrics.ItemBottomPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NavBarMetrics.IconLabelGap),
    ) {
        Icon(
            painter = painterResource(destination.icon),
            // The label beside it already names the tab; announcing it twice is noise.
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .background(indicatorColor, CircleShape)
                .padding(
                    horizontal = NavBarMetrics.IndicatorHorizontalPadding,
                    vertical = NavBarMetrics.IndicatorVerticalPadding,
                )
                .size(NavBarMetrics.IconSize),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = NavBarMetrics.LabelTracking,
            ),
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * The tab whose back stack is showing, re-read whenever the top entry changes — any push or pop
 * moves it, and [owningTab] reads ownership off the whole stack rather than off that entry.
 */
@Composable
private fun rememberSelectedTab(navController: NavController): TopLevelDestination? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return remember(navBackStackEntry) { navController.owningTab() }
}

/**
 * Handles a tap on [destination]: re-selecting the tab the user is already in pops back to that
 * tab's root (the platform convention); any other tab is switched to, restoring its saved stack.
 */
internal fun NavController.selectTab(destination: TopLevelDestination) {
    if (destination == owningTab()) {
        popBackStack(destination.appDestination.route, inclusive = false)
    } else {
        navigateToTab(destination)
    }
}

/**
 * Switches tabs without stacking them: pop to the graph's start, keep each tab's own back stack,
 * and never start a second copy of a tab already on top.
 */
internal fun NavController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.appDestination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The tab whose back stack the current screen belongs to, or `null` when no tab root is on the
 * stack (no graph yet, e.g. a preview).
 *
 * Read off the back stack, not the current route: shared screens (a meal plan's detail, a recipe)
 * are reachable from more than one tab, so their route alone cannot say whose stack they are in —
 * a plan created from a recipe lives in the Recipes stack, and highlighting Meal Plans there made
 * the Recipes tab look unselected while tapping it restored the very same plan.
 *
 * The graph is flat, and [navigateToTab] always pops to the start destination before pushing a
 * tab, so a stack is `[start tab root, (other tab root, …)]`: the other tab's root, when present,
 * owns everything above it; otherwise the start tab does.
 */
internal fun NavController.owningTab(): TopLevelDestination? {
    val rootsOnStack = TopLevelDestination.entries.filter { hasBackStackEntry(it.appDestination.route) }
    if (rootsOnStack.isEmpty()) return null
    val startRoute = graph.startDestinationRoute
    return rootsOnStack.firstOrNull { it.appDestination.route != startRoute } ?: rootsOnStack.first()
}

/** [NavController.getBackStackEntry] reports a miss by throwing; this is the boolean it implies. */
private fun NavController.hasBackStackEntry(route: String): Boolean = try {
    getBackStackEntry(route)
    true
} catch (_: IllegalArgumentException) {
    false
}

@Preview(name = "Bottom nav — light")
@Preview(name = "Bottom nav — dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BottomNavigationBarPreview() {
    ChefAITheme {
        BottomNavigationBar(navController = NavController(LocalContext.current))
    }
}

@Preview(name = "Nav rail — light", heightDp = 360)
@Preview(name = "Nav rail — dark", heightDp = 360, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun NavigationRailBarPreview() {
    ChefAITheme {
        NavigationRailBar(navController = NavController(LocalContext.current))
    }
}
