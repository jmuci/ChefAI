package com.tenmilelabs.chefai.domain.model

import java.util.UUID

data class User(
    val uuid: UUID,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?
)
