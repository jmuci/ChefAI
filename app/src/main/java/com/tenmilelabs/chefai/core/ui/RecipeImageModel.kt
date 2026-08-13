package com.tenmilelabs.chefai.core.ui

/**
 * Resolves what Coil should load for a recipe image: an on-device copy if one was cached at import
 * time (see [com.tenmilelabs.chefai.recipes.domain.usecase.CacheRecipeImage]), falling back to the
 * remote URL, or `null` if neither exists.
 *
 * The `null` fallback matters on its own, independent of local caching: Coil 3 throws
 * `Unable to create a fetcher that supports:` given an empty-string model rather than treating it
 * as "no image" — see docs/claude/gotchas.md #1.
 */
fun recipeImageModel(localImagePath: String?, remoteUrl: String): String? =
    localImagePath?.let { "file://$it" } ?: remoteUrl.ifEmpty { null }
