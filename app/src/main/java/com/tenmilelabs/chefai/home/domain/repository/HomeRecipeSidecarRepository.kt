package com.tenmilelabs.chefai.home.domain.repository

import com.tenmilelabs.chefai.home.data.model.HomeSidecarDto

/**
 * Upserts recipes (and their related tags, labels, creator) from a layout sidecar into Room,
 * so that SDUI cards have backing data even on a fresh install.
 */
interface HomeRecipeSidecarRepository {
    /**
     * Upserts all entities from [sidecar] into Room.
     * Skips recipes that already have [com.tenmilelabs.chefai.core.data.local.util.SyncState.PENDING]
     * or [com.tenmilelabs.chefai.core.data.local.util.SyncState.SYNCED] state.
     *
     * @return the number of recipes actually written.
     */
    suspend fun upsertSidecar(sidecar: HomeSidecarDto): Int
}
