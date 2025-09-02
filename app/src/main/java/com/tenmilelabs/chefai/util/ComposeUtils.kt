package com.tenmilelabs.chefai.util

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R

@Composable
fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = modifier.width(100.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun EmptyContent(
    @StringRes title: Int,
    @StringRes subtitle: Int,
    @DrawableRes noRecipesIconRes: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = noRecipesIconRes),
            contentDescription = stringResource(title),
            modifier = Modifier
                .size(120.dp)
                .alpha(0.2f)
        )
        Text(
            text = stringResource(id = title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_medium))
        )

        Text(
            text = stringResource(id = subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.padding_extra_large))
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyContentPreview() {
    EmptyContent(
        title = R.string.no_recipes_title,
        subtitle = R.string.no_recipes_subtitle,
        noRecipesIconRes = R.drawable.ic_chef_hat_black_24dp,
    )
}

@Preview(showBackground = true)
@Composable
fun LoadingContentPreview() {
    LoadingContent(
        modifier = Modifier
    )
}

