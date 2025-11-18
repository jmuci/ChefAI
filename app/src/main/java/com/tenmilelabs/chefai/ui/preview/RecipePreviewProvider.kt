package com.tenmilelabs.chefai.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.tenmilelabs.chefai.domain.model.RecipePreview

class RecipePreviewProvider : PreviewParameterProvider<RecipePreview> {
    override val values = PreviewData.recipePreviewList.asSequence()
}
