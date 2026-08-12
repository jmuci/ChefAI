package com.tenmilelabs.chefai.recipes.ui.urlimport

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.core.data.local.room.dao.RecipeDraftDao
import com.tenmilelabs.chefai.core.di.IoDispatcher
import com.tenmilelabs.chefai.recipes.data.mapper.toRecipeDraftEntity
import com.tenmilelabs.chefai.recipes.domain.model.RecipeImportResult
import com.tenmilelabs.chefai.recipes.domain.repository.RecipeImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ImportRecipeViewModel @Inject constructor(
    private val recipeImporter: RecipeImporter,
    private val recipeDraftDao: RecipeDraftDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportRecipeState())
    val state: StateFlow<ImportRecipeState> = _state.asStateFlow()

    private val _effects = Channel<ImportEffect>(Channel.BUFFERED)
    val effects: Flow<ImportEffect> = _effects.receiveAsFlow()

    fun dispatch(action: ImportAction) {
        when (action) {
            is ImportAction.UrlChanged -> _state.update {
                it.copy(url = action.url, errorRes = null, showManualEntryOption = false)
            }

            ImportAction.Import -> import()
            ImportAction.ClearError -> _state.update { it.copy(errorRes = null, showManualEntryOption = false) }
            ImportAction.EnterManually -> viewModelScope.launch { _effects.send(ImportEffect.NavigateToManualEditor) }
        }
    }

    private fun import() {
        if (!_state.value.canImport) return
        val url = _state.value.url

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, errorRes = null, showManualEntryOption = false) }

            when (val result = recipeImporter.import(url)) {
                is RecipeImportResult.Success -> {
                    withContext(ioDispatcher) {
                        recipeDraftDao.saveDraft(result.draft.toRecipeDraftEntity())
                    }
                    _state.update { it.copy(isImporting = false) }
                    _effects.send(ImportEffect.NavigateToEditorWithDraft(result.draft.recipeId))
                }

                RecipeImportResult.InvalidUrl -> failWith(R.string.import_recipe_invalid_url)

                RecipeImportResult.NoRecipeFound -> _state.update {
                    it.copy(
                        isImporting = false,
                        errorRes = R.string.import_recipe_no_recipe_found,
                        showManualEntryOption = true,
                    )
                }

                is RecipeImportResult.NetworkError -> failWith(R.string.import_recipe_network_error)
                is RecipeImportResult.ParseError -> failWith(R.string.import_recipe_parse_error)
            }
        }
    }

    private fun failWith(@StringRes messageRes: Int) {
        _state.update { it.copy(isImporting = false, errorRes = messageRes, showManualEntryOption = false) }
    }
}
