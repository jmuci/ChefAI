package com.tenmilelabs.chefai.core.domain.model

import java.util.UUID

data class SourceClassification(
    val uuid: UUID,
    val category: String,
    val subcategory: String?
)
