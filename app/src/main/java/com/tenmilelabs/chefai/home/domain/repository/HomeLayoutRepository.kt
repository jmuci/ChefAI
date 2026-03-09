package com.tenmilelabs.chefai.home.domain.repository

import com.tenmilelabs.chefai.home.data.model.HomeLayoutModel
import kotlinx.coroutines.flow.Flow

interface HomeLayoutRepository {
    /** Emits cached layout immediately, then network-refreshed layout if checksum differs. */
    fun getHomeLayout(): Flow<HomeLayoutModel>
}
