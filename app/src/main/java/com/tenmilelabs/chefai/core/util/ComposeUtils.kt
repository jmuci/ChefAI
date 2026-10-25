package com.tenmilelabs.chefai.core.util

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tenmilelabs.chefai.R

/**
 * Loading state: the ChefAI icon rendered in grayscale with a light band sweeping across it.
 */
@Composable
fun LoadingContent(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "loadingShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = LinearEasing),
        ),
        label = "loadingShimmerProgress",
    )
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            modifier = Modifier
                .size(LOADING_ICON_SIZE)
                .drawWithContent {
                    val bandWidth = size.width * 0.6f
                    val startX = -bandWidth + (size.width + bandWidth * 2) * progress
                    // The icon's art sits on an opaque white square; Multiply makes that square
                    // vanish into the surface while the gray linework stays.
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(size.toRect(), Paint().apply { blendMode = BlendMode.Multiply })
                        drawContent()
                        // SrcAtop lightens only pixels that are already painted.
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.9f), Color.Transparent),
                                start = Offset(startX, 0f),
                                end = Offset(startX + bandWidth, bandWidth * 0.4f),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                        canvas.restore()
                    }
                }
        )
    }
}

private val LOADING_ICON_SIZE = 160.dp

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

