package com.tenmilelabs.chefai.search.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.components.Panel
import com.tenmilelabs.chefai.core.ui.components.PanelMetrics
import com.tenmilelabs.chefai.core.ui.components.SectionHeaderWithSubtitle
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.core.ui.theme.chefColors
import com.tenmilelabs.chefai.search.ui.model.SearchCategory
import com.tenmilelabs.chefai.search.ui.model.SearchCategoryGroup

/** 8dp, per the handoff's category-card grid gap. */
private val GridGap = 8.dp

private const val Columns = 2

/**
 * The Search tab's landing page: browse shortcuts grouped into sections, two cards per row. Each
 * group sits on its own panel (docs/design/modernist.md § 3.1) over `chefColors.panelGround`, so
 * groups separate by color and space rather than a rule. Stateless — the caller decides what a tap
 * does.
 *
 * The catalog is small and static, so each group's grid is composed eagerly inside its panel
 * rather than as lazy grid cells — a panel has to wrap its cards to draw one surface around them.
 */
@Composable
fun SearchBrowseContent(
    onCategoryClick: (SearchCategory) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.chefColors.panelGround)
            .testTag("SearchBrowseContent"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(PanelMetrics.Gap),
    ) {
        items(items = SearchCategoryGroup.entries, key = { it.name }) { group ->
            Panel {
                SectionHeaderWithSubtitle(title = stringResource(group.titleRes), subtitle = "")
                CategoryGrid(
                    categories = SearchCategory.of(group),
                    onCategoryClick = onCategoryClick,
                    modifier = Modifier.padding(
                        horizontal = dimensionResource(R.dimen.padding_medium),
                    ),
                )
            }
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<SearchCategory>,
    onCategoryClick: (SearchCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        categories.chunked(Columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                row.forEachIndexed { columnIndex, category ->
                    val index = rowIndex * Columns + columnIndex
                    CategoryCard(
                        title = stringResource(category.labelRes),
                        tone = CategoryCardTone.entries[index % CategoryCardTone.entries.size],
                        onClick = { onCategoryClick(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // An odd last card keeps its half width instead of stretching across the row.
                repeat(Columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun SearchBrowseContentLightPreview() {
    ChefAITheme {
        Surface {
            SearchBrowseContent(onCategoryClick = {})
        }
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun SearchBrowseContentDarkPreview() {
    ChefAITheme(darkTheme = true) {
        Surface {
            SearchBrowseContent(onCategoryClick = {})
        }
    }
}
