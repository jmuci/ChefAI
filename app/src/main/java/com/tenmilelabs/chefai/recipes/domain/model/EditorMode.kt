package com.tenmilelabs.chefai.recipes.domain.model

import java.util.UUID

/**
 * Represents the mode of the recipe editor.
 */
sealed interface EditorMode {
    data object Create : EditorMode
    data class Edit(val recipeId: UUID) : EditorMode
}
