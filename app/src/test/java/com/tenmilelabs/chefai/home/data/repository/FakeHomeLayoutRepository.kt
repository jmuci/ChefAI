package com.tenmilelabs.chefai.home.data.repository

import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.data.model.HomeLayoutModel
import com.tenmilelabs.chefai.home.domain.repository.HomeLayoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

/** Test double for [HomeLayoutRepository]. Allows controlling emitted layouts and error scenarios. */
class FakeHomeLayoutRepository : HomeLayoutRepository {

    private val layoutFlow = MutableSharedFlow<HomeLayoutModel>(replay = 1)
    var shouldError = false

    fun emit(layout: HomeLayoutModel) {
        layoutFlow.tryEmit(layout)
    }

    fun emitDefault() {
        layoutFlow.tryEmit(
            HomeLayoutModel(
                schemaVersion = "1.0.0",
                layoutChecksum = "test-checksum",
                components = listOf(
                    ComponentModel.SectionHeader(
                        id = "header-1",
                        title = "For You",
                        subtitle = "Personalized picks",
                    ),
                    ComponentModel.Carousel(
                        id = "carousel-1",
                        items = listOf(
                            ComponentModel.LargeCard(
                                id = "card-1",
                                recipeId = "00000000-0000-0000-0000-000000000001",
                            ),
                            ComponentModel.SquaredCard(
                                id = "card-2",
                                recipeId = "00000000-0000-0000-0000-000000000002",
                            ),
                        ),
                    ),
                ),
            )
        )
    }

    override fun getHomeLayout(): Flow<HomeLayoutModel> {
        if (shouldError) {
            return flow { throw RuntimeException("Test error from FakeHomeLayoutRepository") }
        }
        return layoutFlow
    }
}
