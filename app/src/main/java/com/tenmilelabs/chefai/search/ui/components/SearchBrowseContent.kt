package com.tenmilelabs.chefai.search.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.ui.theme.ChefAITheme
import com.tenmilelabs.chefai.search.ui.model.SearchCategory
import com.tenmilelabs.chefai.search.ui.model.SearchCategoryGroup

/**
 * The Search tab's landing page: browse shortcuts grouped into sections, two cards per row.
 * Stateless — the caller decides what a tap does.
 */
@Composable
fun SearchBrowseContent(
    onCategoryClick: (SearchCategory) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .testTag("SearchBrowseContent"),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
    ) {
        SearchCategoryGroup.entries.forEach { group ->
            item(
                key = "header_${group.name}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                BrowseSectionHeader(title = stringResource(group.titleRes))
            }
            itemsIndexed(
                items = SearchCategory.of(group),
                key = { _, category -> category.name },
            ) { index, category ->
                CategoryCard(
                    title = stringResource(category.labelRes),
                    tone = CategoryCardTone.entries[index % CategoryCardTone.entries.size],
                    onClick = { onCategoryClick(category) },
                )
            }
        }
    }
}

@Composable
private fun BrowseSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(
            horizontal = dimensionResource(R.dimen.padding_extra_small),
            vertical = dimensionResource(R.dimen.padding_medium),
        ),
    )
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
