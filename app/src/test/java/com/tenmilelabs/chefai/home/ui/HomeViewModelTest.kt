package com.tenmilelabs.chefai.home.ui

import app.cash.turbine.test
import coil3.ImageLoader
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.collections.data.repository.FakeCollectionsRepository
import com.tenmilelabs.chefai.core.testutil.createTestSessionManager
import com.tenmilelabs.chefai.core.testutil.TEST_DOMAIN_RECIPE_PREVIEWS_LIST
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.home.data.model.ComponentModel
import com.tenmilelabs.chefai.home.data.model.HomeLayoutModel
import com.tenmilelabs.chefai.home.data.repository.FakeHomeLayoutRepository
import com.tenmilelabs.chefai.home.data.repository.FakeRecipesRepository
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class HomeViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: HomeViewModel
    private lateinit var homeLayoutRepository: FakeHomeLayoutRepository
    private lateinit var recipesRepository: FakeRecipesRepository
    private val imageLoader: ImageLoader = mockk(relaxed = true)
    private val appContext: android.content.Context = mockk(relaxed = true)
    private lateinit var collectionsRepository: FakeCollectionsRepository

    @Before
    fun setup() {
        homeLayoutRepository = FakeHomeLayoutRepository()
        recipesRepository = FakeRecipesRepository()
        collectionsRepository = FakeCollectionsRepository()

        viewModel = HomeViewModel(
            homeLayoutRepository = homeLayoutRepository,
            recipesRepository = recipesRepository,
            collectionsRepository = collectionsRepository,
            sessionManager = createTestSessionManager(),
            imageLoader = imageLoader,
            appContext = appContext,
        )
    }

    @Test
    fun `initial state is Loading before any layout emitted`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertThat(initial).isInstanceOf(HomeUiState.Loading::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState becomes Success when repository emits a layout with no recipe cards`() = runTest {
        // A layout with only headers (no recipe IDs) should go directly to Success with empty map.
        homeLayoutRepository.emit(
            HomeLayoutModel(
                schemaVersion = "1.0.0",
                components = listOf(
                    ComponentModel.SectionHeader(id = "hdr", title = "Title"),
                ),
            )
        )

        viewModel.uiState.test {
            val state = awaitItem().let {
                if (it is HomeUiState.Loading) awaitItem() else it
            }
            assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
            val success = state as HomeUiState.Success
            assertThat(success.components).hasSize(1)
            assertThat(success.recipes).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState becomes Success when layout and recipes both emit`() = runTest {
        homeLayoutRepository.emitDefault()
        recipesRepository.emitPreviews(emptyList())

        viewModel.uiState.test {
            val state = awaitItem().let {
                if (it is HomeUiState.Loading) awaitItem() else it
            }
            assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Success state components exclude Unknown variants`() = runTest {
        homeLayoutRepository.emit(
            HomeLayoutModel(
                schemaVersion = "1.0.0",
                components = listOf(
                    ComponentModel.SectionHeader(id = "hdr", title = "Title"),
                    ComponentModel.Unknown(id = "unk", originalType = "future_type"),
                    ComponentModel.Carousel(
                        id = "c1",
                        items = listOf(
                            ComponentModel.LargeCard(id = "lc1", recipeId = null),
                        ),
                    ),
                ),
            )
        )
        // Carousel has no recipe IDs, so flatMapLatest emits immediately with empty recipes
        viewModel.uiState.test {
            val state = awaitItem().let {
                if (it is HomeUiState.Loading) awaitItem() else it
            }
            assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
            val components = (state as HomeUiState.Success).components
            assertThat(components.none { it is ComponentModel.Unknown }).isTrue()
            assertThat(components).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState becomes Error and emits ShowSnackbar when repository throws`() = runTest {
        homeLayoutRepository.shouldError = true
        val errorViewModel = HomeViewModel(
            homeLayoutRepository = homeLayoutRepository,
            recipesRepository = recipesRepository,
            collectionsRepository = FakeCollectionsRepository(),
            sessionManager = createTestSessionManager(),
            imageLoader = imageLoader,
            appContext = appContext,
        )

        errorViewModel.uiState.test {
            val state = awaitItem().let {
                if (it is HomeUiState.Loading) awaitItem() else it
            }
            assertThat(state).isInstanceOf(HomeUiState.Error::class.java)
            cancelAndIgnoreRemainingEvents()
        }

        errorViewModel.uiEvents.test {
            val event = awaitItem()
            assertThat(event).isInstanceOf(HomeUiEvent.ShowSnackbar::class.java)
            assertThat((event as HomeUiEvent.ShowSnackbar).message)
                .isEqualTo(R.string.loading_recipes_error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onAction CardClicked with valid UUID emits NavigateToRecipeDetail`() = runTest {
        homeLayoutRepository.emitDefault()
        recipesRepository.emitPreviews(emptyList())
        val validId = "00000000-0000-0000-0000-000000000001"

        viewModel.uiEvents.test {
            viewModel.onAction(HomeAction.CardClicked(validId))
            val event = awaitItem()
            assertThat(event).isInstanceOf(HomeUiEvent.NavigateToRecipeDetail::class.java)
            assertThat((event as HomeUiEvent.NavigateToRecipeDetail).recipeUuid.toString())
                .isEqualTo(validId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onAction CardClicked with invalid UUID logs warning and does not crash`() = runTest {
        homeLayoutRepository.emitDefault()
        recipesRepository.emitPreviews(emptyList())

        viewModel.onAction(HomeAction.CardClicked("not-a-valid-uuid"))
        viewModel.onAction(HomeAction.CardClicked(""))

        viewModel.uiEvents.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState updates when repository emits a new layout`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem()).isInstanceOf(HomeUiState.Loading::class.java)

            // First emission — header only (no recipe IDs → Success immediately)
            homeLayoutRepository.emitDefault()
            recipesRepository.emitPreviews(emptyList())
            val first = awaitItem()
            assertThat(first).isInstanceOf(HomeUiState.Success::class.java)

            // Second emission with header-only layout
            homeLayoutRepository.emit(
                HomeLayoutModel(
                    schemaVersion = "1.0.0",
                    components = listOf(
                        ComponentModel.SectionHeader(id = "new-header", title = "New Section"),
                    ),
                )
            )
            val second = awaitItem()
            assertThat(second).isInstanceOf(HomeUiState.Success::class.java)
            val components = (second as HomeUiState.Success).components
            assertThat(components).hasSize(1)
            assertThat(components[0]).isInstanceOf(ComponentModel.SectionHeader::class.java)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bookmarkedRecipeIds reflects repository state`() = runTest {
        val targetId = TEST_DOMAIN_RECIPE_PREVIEWS_LIST.first().uuid
        collectionsRepository.setBookmarkedIds(setOf(targetId))

        // Header-only layout — no recipe IDs → _bookmarkedIds.map branch fires immediately
        homeLayoutRepository.emit(
            HomeLayoutModel(
                schemaVersion = "1.0.0",
                components = listOf(ComponentModel.SectionHeader(id = "hdr", title = "Title")),
            )
        )

        viewModel.uiState.test {
            val state = awaitItem().let {
                if (it is HomeUiState.Loading) awaitItem() else it
            }
            assertThat(state).isInstanceOf(HomeUiState.Success::class.java)
            assertThat((state as HomeUiState.Success).bookmarkedRecipeIds).contains(targetId)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
