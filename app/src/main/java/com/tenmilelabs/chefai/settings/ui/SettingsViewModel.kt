package com.tenmilelabs.chefai.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.AccountDeletionPreviewResult
import com.tenmilelabs.chefai.auth.domain.model.DeleteAccountResult
import com.tenmilelabs.chefai.auth.domain.model.PublicRecipeDisposition
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.auth.domain.repository.AccountRepository
import com.tenmilelabs.chefai.auth.domain.usecase.DeleteAccountUseCase
import com.tenmilelabs.chefai.core.domain.model.RecipeLanguage
import com.tenmilelabs.chefai.core.domain.model.WeekStart
import com.tenmilelabs.chefai.core.domain.repository.UserPreferencesRepository
import com.tenmilelabs.chefai.core.domain.units.MeasurementSystem
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the settings screen renders. */
data class SettingsUiState(
    val measurementSystem: MeasurementSystem = MeasurementSystem.DEFAULT,
    val fallbackRecipeLanguage: RecipeLanguage = RecipeLanguage.DEFAULT,
    val weekStart: WeekStart = WeekStart.MONDAY,
    val deleteAccount: DeleteAccountUiState = DeleteAccountUiState.Unavailable,
)

/** Everything the user can do on the settings screen. */
sealed interface SettingsAction {
    data class MeasurementSystemChanged(val system: MeasurementSystem) : SettingsAction
    data class FallbackRecipeLanguageChanged(val language: RecipeLanguage) : SettingsAction
    data class WeekStartChanged(val weekStart: WeekStart) : SettingsAction

    /** The section's "Delete account" button: fetches the preview, then opens the dialog. */
    data object DeleteAccountClicked : SettingsAction
    data class PublicRecipesChoiceChanged(val disposition: PublicRecipeDisposition) : SettingsAction
    data class DeleteAccountPasswordChanged(val password: String) : SettingsAction
    data object DeleteAccountPasswordVisibilityToggled : SettingsAction
    data object DeleteAccountConfirmed : SettingsAction
    data object DeleteAccountDismissed : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sessionManager: SessionManager,
    private val accountRepository: AccountRepository,
    private val deleteAccount: DeleteAccountUseCase,
) : ViewModel() {

    /** The flow's own progress; [uiState] hides it behind [DeleteAccountUiState.Unavailable]. */
    private val deleteAccountState = MutableStateFlow<DeleteAccountUiState>(DeleteAccountUiState.Idle)

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.measurementSystem,
        userPreferencesRepository.fallbackRecipeLanguage,
        userPreferencesRepository.weekStart,
        sessionManager.userSession,
        deleteAccountState,
    ) { measurementSystem, fallbackRecipeLanguage, weekStart, session, deleteAccount ->
        SettingsUiState(
            measurementSystem = measurementSystem,
            fallbackRecipeLanguage = fallbackRecipeLanguage,
            weekStart = weekStart,
            deleteAccount = if (session is UserSession.Authenticated) {
                deleteAccount
            } else {
                DeleteAccountUiState.Unavailable
            },
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = SettingsUiState(),
        )

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.MeasurementSystemChanged -> viewModelScope.launch {
                userPreferencesRepository.setMeasurementSystem(action.system)
            }
            is SettingsAction.FallbackRecipeLanguageChanged -> viewModelScope.launch {
                userPreferencesRepository.setFallbackRecipeLanguage(action.language)
            }

            is SettingsAction.WeekStartChanged -> viewModelScope.launch {
                userPreferencesRepository.setWeekStart(action.weekStart)
            }

            SettingsAction.DeleteAccountClicked -> loadDeletionPreview()
            is SettingsAction.PublicRecipesChoiceChanged -> updateConfirming {
                it.copy(publicRecipes = action.disposition, error = it.error.clearedByChoice())
            }
            is SettingsAction.DeleteAccountPasswordChanged -> updateConfirming {
                it.copy(password = action.password, error = it.error.clearedByPasswordEdit())
            }
            SettingsAction.DeleteAccountPasswordVisibilityToggled -> updateConfirming {
                it.copy(isPasswordVisible = !it.isPasswordVisible)
            }
            SettingsAction.DeleteAccountConfirmed -> submitDeletion()
            SettingsAction.DeleteAccountDismissed -> deleteAccountState.update { state ->
                // Mid-request the dialog stays: the deletion may already have happened.
                if (state is DeleteAccountUiState.Confirming && state.isSubmitting) state else DeleteAccountUiState.Idle
            }
        }
    }

    private fun loadDeletionPreview() {
        if (deleteAccountState.value == DeleteAccountUiState.LoadingPreview) return
        deleteAccountState.value = DeleteAccountUiState.LoadingPreview
        viewModelScope.launch {
            deleteAccountState.value = when (val result = accountRepository.getDeletionPreview()) {
                is AccountDeletionPreviewResult.Success -> DeleteAccountUiState.Confirming(result.preview)
                AccountDeletionPreviewResult.SessionExpired ->
                    DeleteAccountUiState.PreviewFailed(DeleteAccountError.SESSION_EXPIRED)
                AccountDeletionPreviewResult.Failed ->
                    DeleteAccountUiState.PreviewFailed(DeleteAccountError.PREVIEW_FAILED)
            }
        }
    }

    private fun submitDeletion() {
        val confirming = deleteAccountState.value as? DeleteAccountUiState.Confirming ?: return
        if (!confirming.canSubmit) return
        deleteAccountState.value = confirming.copy(isSubmitting = true, error = null)

        viewModelScope.launch {
            val result = deleteAccount(
                password = confirming.password,
                preview = confirming.preview,
                publicRecipes = confirming.publicRecipes,
            )
            if (result == DeleteAccountResult.Deleted) {
                deleteAccountState.value = DeleteAccountUiState.Idle
                _effects.send(SettingsEffect.AccountDeleted)
                return@launch
            }
            updateConfirming { it.afterFailure(result) }
        }
    }

    private fun updateConfirming(transform: (DeleteAccountUiState.Confirming) -> DeleteAccountUiState.Confirming) {
        deleteAccountState.update { state ->
            if (state is DeleteAccountUiState.Confirming) transform(state) else state
        }
    }
}

private fun DeleteAccountUiState.Confirming.afterFailure(result: DeleteAccountResult): DeleteAccountUiState.Confirming {
    val stopped = copy(isSubmitting = false)
    return when (result) {
        // Not reachable here; handled before this is called.
        DeleteAccountResult.Deleted -> stopped
        DeleteAccountResult.WrongPassword -> stopped.copy(error = DeleteAccountError.WRONG_PASSWORD)
        is DeleteAccountResult.PublicRecipeChoiceRequired ->
            if (preview.publicRecipeCount == result.publicRecipeCount && preview.requiresPublicRecipeChoice) {
                stopped.copy(error = DeleteAccountError.PUBLIC_RECIPE_CHOICE_MISSING)
            } else {
                // The preview was stale: show the choice with the server's own count.
                stopped.copy(
                    preview = preview.copy(publicRecipeCount = result.publicRecipeCount),
                    publicRecipes = null,
                    error = DeleteAccountError.PUBLIC_RECIPES_CHANGED,
                )
            }
        DeleteAccountResult.Conflict -> stopped.copy(error = DeleteAccountError.CONFLICT)
        DeleteAccountResult.RateLimited -> stopped.copy(error = DeleteAccountError.RATE_LIMITED)
        DeleteAccountResult.SessionExpired -> stopped.copy(error = DeleteAccountError.SESSION_EXPIRED)
        DeleteAccountResult.Failed -> stopped.copy(error = DeleteAccountError.GENERIC)
    }
}

private fun DeleteAccountError?.clearedByChoice(): DeleteAccountError? = when (this) {
    DeleteAccountError.PUBLIC_RECIPE_CHOICE_MISSING, DeleteAccountError.PUBLIC_RECIPES_CHANGED -> null
    else -> this
}

private fun DeleteAccountError?.clearedByPasswordEdit(): DeleteAccountError? = when (this) {
    DeleteAccountError.WRONG_PASSWORD -> null
    else -> this
}
