package com.tenmilelabs.chefai.ui.preview

import com.tenmilelabs.chefai.data.source.local.util.generateUuid7
import com.tenmilelabs.chefai.domain.model.User

object SharedData {
    val user = User(
        uuid = generateUuid7(),
        displayName = "ChefAI Preview",
        email = "preview@chefai.app",
        avatarUrl = "https://i.pravatar.cc/150?u=a042581f4e29026704d"
    )
}