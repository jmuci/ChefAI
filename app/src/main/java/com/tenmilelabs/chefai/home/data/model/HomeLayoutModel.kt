package com.tenmilelabs.chefai.home.data.model

import kotlinx.serialization.Serializable

/**
 * Top-level model for the home screen server-driven layout.
 *
 * @property schemaVersion Semantic version of the layout schema (e.g., "1.0.0").
 *   Clients check this to determine if they can render the payload.
 * @property layoutChecksum Optional content checksum for cache invalidation.
 *   Clients compare this with their cached copy to skip redundant updates.
 * @property components Ordered list of UI components to render on the home screen.
 */
@Serializable
data class HomeLayoutModel(
    val schemaVersion: String,
    val layoutChecksum: String? = null,
    val components: List<ComponentModel> = emptyList(),
)
