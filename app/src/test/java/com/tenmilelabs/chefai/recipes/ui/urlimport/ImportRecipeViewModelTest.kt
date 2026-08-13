package com.tenmilelabs.chefai.recipes.ui.urlimport

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URLEncoder
import java.util.UUID

@ExperimentalCoroutinesApi
class ImportRecipeViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var recipeImporter: FakeRecipeImporter
    private lateinit var recipeDraftDao: FakeRecipeDraftDao
    private lateinit var viewModel: ImportRecipeViewModel

    @Before
    fun setup() {
        recipeImporter = FakeRecipeImporter()
        recipeDraftDao = FakeRecipeDraftDao()
        viewModel = createViewModel()
    }

    private fun createViewModel(prefillUrl: String? = null): ImportRecipeViewModel {
        val savedStateHandle = SavedStateHandle().apply {
            if (prefillUrl != null) {
                set(AppDestinationArgs.PREFILL_URL_ARG, URLEncoder.encode(prefillUrl, "UTF-8"))
            }
        }
        return ImportRecipeViewModel(
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
    fun `success persists a draft and emits the nav effect with the matching id`() = runTest {
        val draftId = UUID.randomUUID()
        recipeImporter.result = RecipeImportResult.Success(
            RecipeDraft(recipeId = draftId, isNewRecipe = true, title = "Mock Recipe"),
        )

        viewModel.effects.test {
            viewModel.dispatch(ImportAction.UrlChanged("https://example.com/recipe"))
            viewModel.dispatch(ImportAction.Import)

            val effect = awaitItem()
            assertThat(effect).isEqualTo(ImportEffect.NavigateToEditorWithDraft(draftId))
        }

        assertThat(recipeDraftDao.getDraft(draftId)).isNotNull()
        assertThat(viewModel.state.value.isImporting).isFalse()
    }

    @Test
    fun `invalid url sets an error and emits no nav effect`() = runTest {
        recipeImporter.result = RecipeImportResult.InvalidUrl

        viewModel.dispatch(ImportAction.UrlChanged("not a url"))
        viewModel.dispatch(ImportAction.Import)

        assertThat(viewModel.state.value.errorRes).isEqualTo(R.string.import_recipe_invalid_url)
        assertThat(viewModel.state.value.isImporting).isFalse()
    }

    @Test
    fun `no recipe found sets an error and offers manual entry`() = runTest {
        recipeImporter.result = RecipeImportResult.NoRecipeFound

        viewModel.dispatch(ImportAction.UrlChanged("https://example.com/blog"))
        viewModel.dispatch(ImportAction.Import)

        assertThat(viewModel.state.value.errorRes).isEqualTo(R.string.import_recipe_no_recipe_found)
        assertThat(viewModel.state.value.showManualEntryOption).isTrue()
    }

    @Test
    fun `network error sets an error message`() = runTest {
        recipeImporter.result = RecipeImportResult.NetworkError("timeout")

        viewModel.dispatch(ImportAction.UrlChanged("https://example.com/recipe"))
        viewModel.dispatch(ImportAction.Import)

        assertThat(viewModel.state.value.errorRes).isEqualTo(R.string.import_recipe_network_error)
    }

    @Test
    fun `parse error sets an error message`() = runTest {
        recipeImporter.result = RecipeImportResult.ParseError("boom")

        viewModel.dispatch(ImportAction.UrlChanged("https://example.com/recipe"))
        viewModel.dispatch(ImportAction.Import)

        assertThat(viewModel.state.value.errorRes).isEqualTo(R.string.import_recipe_parse_error)
    }

    @Test
    fun `NeedsBrowser hands the url to the in-app browser instead of showing an error`() = runTest {
        recipeImporter.result = RecipeImportResult.NeedsBrowser("https://blocked.example.com/recipe")

        viewModel.effects.test {
            viewModel.dispatch(ImportAction.UrlChanged("https://blocked.example.com/recipe"))
            viewModel.dispatch(ImportAction.Import)

            val effect = awaitItem()
            assertThat(effect)
                .isEqualTo(ImportEffect.NavigateToBrowserImport("https://blocked.example.com/recipe"))
        }

        assertThat(viewModel.state.value.errorRes).isNull()
        assertThat(viewModel.state.value.isImporting).isFalse()
    }

    @Test
    fun `EnterManually emits the manual editor nav effect`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(ImportAction.EnterManually)
            assertThat(awaitItem()).isEqualTo(ImportEffect.NavigateToManualEditor)
        }
    }

    @Test
    fun `Import is a no-op while a previous import is still in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        recipeImporter.gate = gate
        recipeImporter.result = RecipeImportResult.InvalidUrl

        viewModel.dispatch(ImportAction.UrlChanged("https://example.com/recipe"))
        viewModel.dispatch(ImportAction.Import)
        assertThat(viewModel.state.value.isImporting).isTrue()

        viewModel.dispatch(ImportAction.Import)
        assertThat(recipeImporter.callCount).isEqualTo(1)

        gate.complete(Unit)
    }

    @Test
    fun `Import does nothing when the url is blank`() = runTest {
        viewModel.dispatch(ImportAction.Import)

        assertThat(recipeImporter.callCount).isEqualTo(0)
    }

    @Test
    fun `seeds the url field from a PREFILL_URL_ARG passed via SavedStateHandle`() = runTest {
        val prefilled = createViewModel(prefillUrl = "https://example.com/shared-recipe")

        assertThat(prefilled.state.value.url).isEqualTo("https://example.com/shared-recipe")
        assertThat(prefilled.state.value.canImport).isTrue()
    }

    @Test
    fun `starts with an empty url when there is no PREFILL_URL_ARG`() = runTest {
        assertThat(viewModel.state.value.url).isEqualTo("")
    }
}
