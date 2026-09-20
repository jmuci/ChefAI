package com.tenmilelabs.chefai.core.ui.components.flat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.core.ui.theme.chefColors

/**
 * The ruled-grid rhythm: **2dp above a group, 1dp between its rows, 2dp below**.
 *
 * This system organizes by rule weight rather than by cards and shadows, so this rhythm is the
 * layout — it appears on every list in all 19 screens. Getting it by hand means three divider call
 * sites and an `if (index > 0)`, which is three chances to use the wrong weight or the wrong color.
 * Reach for [RuledGroup] instead and the rules are not yours to get wrong:
 *
 * ```
 * // Homogeneous list — the common case.
 * RuledGroup(items = members) { member ->
 *     HouseholdMemberRow(member)
 * }
 *
 * // Heterogeneous rows — Settings, the profile menu.
 * RuledGroup {
 *     row { HouseholdRow(onClick = …) }
 *     row { SettingsRow(onClick = …) }
 *     row { LogoutRow(onClick = …) }
 * }
 * ```
 *
 * Rules run **full-bleed**, edge to edge; they are never inset to match a row's content padding.
 * That is why [RuledGroup] takes no horizontal padding of its own — the row puts its padding
 * inside the rules. An empty group renders nothing rather than two stacked rules.
 *
 * Inside a `LazyColumn`, where the rows are `item`s rather than children of one `Column`, drop to
 * [SectionRule] and [RowRule] and place them yourself.
 *
 * See `docs/design/modernist.md` § Rule rhythm.
 */

/** The heavy 2dp rule: above the first row of a group and below its last. */
@Composable
fun SectionRule(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = MaterialTheme.chefColors.sectionRuleWidth,
        color = MaterialTheme.colorScheme.outline,
    )
}

/** The 1dp hairline between rows *inside* a group. Never at a group's edge — that is a [SectionRule]. */
@Composable
fun RowRule(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = MaterialTheme.chefColors.rowRuleWidth,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A list group with the rule rhythm applied: 2dp, rows separated by 1dp, 2dp.
 *
 * @param items one row per element, in order. Empty renders nothing.
 * @param key what identifies a row across recompositions. The default — the item itself — is right
 *   for enums, strings and value objects. **Pass a stable id (`key = { it.id }`) for anything
 *   whose contents can change**, otherwise editing an item looks like replacing it and the row
 *   loses whatever it remembered.
 * @param row the row body. It supplies its own padding and its own `heightIn(min = 44.dp)`; see
 *   [flatClickable] for the tappable-row recipe.
 */
@Composable
fun <T> RuledGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    key: (T) -> Any? = { item -> item },
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier) {
        SectionRule()
        items.forEachIndexed { index, item ->
            // Every iteration invokes the same lambda, so without an explicit key Compose matches
            // rows positionally: deleting the second of four items composes the third into the
            // second's slot, and it inherits whatever that slot remembered — a stuck pressed tint
            // from `flatClickable`'s interaction source, a half-finished `animateDpAsState`, any
            // `remember` the screen put in its own row.
            key(key(item)) {
                if (index > 0) RowRule()
                row(item)
            }
        }
        SectionRule()
    }
}

/**
 * The same rhythm for a fixed set of *different* rows, declared one `row { }` at a time.
 *
 * ```
 * RuledGroup {
 *     row { ProfileRow(…) }
 *     if (isSignedIn) row { LogoutRow(…) }
 * }
 * ```
 *
 * Rows added conditionally are simply absent — the 1dp hairlines re-flow around them, so there is
 * no way to leave a dangling divider.
 */
@Composable
fun RuledGroup(
    modifier: Modifier = Modifier,
    content: RuledGroupScope.() -> Unit,
) {
    val rows = RuledGroupScope().apply(content).rows
    if (rows.isEmpty()) return
    Column(modifier) {
        SectionRule()
        rows.forEachIndexed { index, row ->
            if (index > 0) RowRule()
            row()
        }
        SectionRule()
    }
}

/** Collects the rows declared inside a [RuledGroup] block. */
class RuledGroupScope internal constructor() {

    internal val rows = mutableListOf<@Composable () -> Unit>()

    /** Declares one row. Called in declaration order; the rules are inserted between them. */
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

// ── previews ──────────────────────────────────────────────────────────────────────────────────

@LightDarkPreview
@Composable
private fun RuledGroupPreview() {
    FlatPreviewSurface {
        PreviewStateLabel("Group of three")
        RuledGroup(items = listOf("Household", "Settings", "Log out")) { label ->
            PreviewRow(label)
        }
        PreviewStateLabel("Group of one — 2dp top and bottom, no hairline")
        RuledGroup(items = listOf("Only row")) { label ->
            PreviewRow(label)
        }
        PreviewStateLabel("Scope form")
        RuledGroup {
            row { PreviewRow("First") }
            row { PreviewRow("Second") }
        }
        PreviewStateLabel("Bare rules")
        SectionRule()
        RowRule()
    }
}

@Composable
private fun PreviewRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinHitTarget)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
