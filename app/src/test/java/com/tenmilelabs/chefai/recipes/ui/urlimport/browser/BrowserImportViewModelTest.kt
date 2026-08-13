package com.tenmilelabs.chefai.recipes.ui.urlimport.browser

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.room.dao.FakeRecipeDraftDao
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.core.util.MainCoroutineRule
import com.tenmilelabs.chefai.recipes.data.repository.FakeRecipeImporter
import com.tenmilelabs.chefai.recipes.domain.model.RecipeDraft
import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult
import com.tenmilelabs.chefai.recipes.domain.usecase.CacheRecipeImage
import com.tenmilelabs.chefai.recipes.domain.usecase.SaveImportedDraft
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URLEncoder
import java.util.UUID

private const val BLOCKED_URL = "https://blocked.example.com/recipe"

@ExperimentalCoroutinesApi
class BrowserImportViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var recipeImporter: FakeRecipeImporter
    private lateinit var recipeDraftDao: FakeRecipeDraftDao
    private lateinit var viewModel: BrowserImportViewModel

    @Before
    fun setup() {
        recipeImporter = FakeRecipeImporter()
        recipeDraftDao = FakeRecipeDraftDao()
        viewModel = createViewModel()
    }

    private fun createViewModel(url: String = BLOCKED_URL): BrowserImportViewModel {
        val savedStateHandle = SavedStateHandle().apply {
            set(AppDestinationArgs.IMPORT_URL_ARG, URLEncoder.encode(url, "UTF-8"))
        }
        return BrowserImportViewModel(
            recipeImporter = recipeImporter,
            saveImportedDraft = SaveImportedDraft(
                recipeDraftDao,
                mockk<CacheRecipeImage>(relaxed = true),
                mainCoroutineRule.testDispatcher,
            ),
            savedStateHandle = savedStateHandle,
        )
    }

    @Test
    fun `decodes the url from the route argument`() = runTest {
        assertThat(viewModel.state.value.url).isEqualTo(BLOCKED_URL)
    }

    @Test
    fun `a snapshot carrying a recipe persists a draft and hands off to the editor`() = runTest {
        val draftId = UUID.randomUUID()
        recipeImporter.result = RecipeImportResult.Success(
            RecipeDraft(recipeId = draftId, isNewRecipe = true, title = "Blocked Recipe"),
        )

        viewModel.effects.test {
            viewModel.dispatch(BrowserImportAction.SnapshotCaptured("<html>recipe</html>"))

            assertThat(awaitItem()).isEqualTo(BrowserImportEffect.NavigateToEditorWithDraft(draftId))
        }

        assertThat(recipeDraftDao.getDraft(draftId)).isNotNull()
        assertThat(recipeImporter.lastImportedHtml).isEqualTo("<html>recipe</html>")
        assertThat(recipeImporter.lastImportedUrl).isEqualTo(BLOCKED_URL)
    }

    @Test
    fun `a snapshot with no recipe keeps polling rather than failing`() = runTest {
        // What every snapshot looks like while the bot check is still on screen.
        recipeImporter.result = RecipeImportResult.NoRecipeFound

        viewModel.dispatch(BrowserImportAction.SnapshotCaptured("<html>challenge</html>"))

        assertThat(viewModel.state.value.isPolling).isTrue()
        assertThat(viewModel.state.value.errorRes).isNull()
    }

    @Test
    fun `ignores a snapshot that lands while the previous one is still being parsed`() = runTest {
        recipeImporter.gate = kotlinx.coroutines.CompletableDeferred()
        recipeImporter.result = RecipeImportResult.NoRecipeFound

        viewModel.dispatch(BrowserImportAction.SnapshotCaptured("<html>first</html>"))
        viewModel.dispatch(BrowserImportAction.SnapshotCaptured("<html>second</html>"))

        assertThat(recipeImporter.callCount).isEqualTo(1)
        recipeImporter.gate?.complete(Unit)
    }

    @Test
    fun `the settle timeout switches the hint to ask the user to act`() = runTest {
        assertThat(viewModel.state.value.phase).isEqualTo(BrowserImportState.Phase.LOADING)

        viewModel.dispatch(BrowserImportAction.SettleTimeoutElapsed)

        assertThat(viewModel.state.value.phase).isEqualTo(BrowserImportState.Phase.AWAITING_USER)
    }

    @Test
    fun `giving up stops the polling and offers manual entry`() = runTest {
        viewModel.dispatch(BrowserImportAction.GiveUp)

        assertThat(viewModel.state.value.errorRes).isEqualTo(R.string.import_recipe_browser_gave_up)
        assertThat(viewModel.state.value.isPolling).isFalse()
    }

    @Test
    fun `EnterManually and Cancel emit their nav effects`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(BrowserImportAction.EnterManually)
            assertThat(awaitItem()).isEqualTo(BrowserImportEffect.NavigateToManualEditor)

            viewModel.dispatch(BrowserImportAction.Cancel)
            assertThat(awaitItem()).isEqualTo(BrowserImportEffect.NavigateBack)
        }
    }
}
