package com.tenmilelabs.chefai.core.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.tenmilelabs.chefai.core.domain.model.RecipePreview

class RecipePreviewProvider : PreviewParameterProvider<RecipePreview> {
    override val values = PreviewData.recipePreviewList.asSequence()
}
