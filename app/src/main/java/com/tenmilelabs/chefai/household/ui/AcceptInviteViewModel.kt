package com.tenmilelabs.chefai.household.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.ui.navigation.AppDestinationArgs
import com.tenmilelabs.chefai.household.data.network.HouseholdApiException
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvitePreview
import com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome
import com.tenmilelabs.chefai.household.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AcceptInviteUiState {
    /**
     * No token yet — the manual-entry path this screen supports from A6 onward. An App Link
     * (added in A7) always arrives with a token already in hand and skips this state entirely.
     */
    data class EnterCode(val code: String = "") : AcceptInviteUiState
    data object Loading : AcceptInviteUiState
    data class Preview(val preview: HouseholdInvitePreview, val token: String) : AcceptInviteUiState
    data object InvalidOrExpired : AcceptInviteUiState
    data object AlreadyInAHousehold : AcceptInviteUiState
    data object Joined : AcceptInviteUiState

    /**
     * An anonymous session previewed a valid invite but can't act on it. Explicit, not a silent
     * redirect — the user's anonymous recipes are real data and deserve a beat of visibility
     * before anything happens to them (see ADR-014 §7).
     *
     * Signing in from here does **not** yet auto-resume this join — that requires persisting the
     * pending token across the sign-up round trip (ADR-014 §7, PR A9). For now the user re-enters
     * the code after signing in via [HouseholdUiState.NoHousehold]'s own entry point.
     */
    data object RequiresSignIn : AcceptInviteUiState

    /** A transient failure, not "this invite doesn't work" — see [AcceptInviteViewModel.onRetry]. */
    data class Error(val message: String) : AcceptInviteUiState
}

@HiltViewModel
class AcceptInviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val householdRepository: HouseholdRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val initialToken: String? = savedStateHandle[AppDestinationArgs.INVITE_TOKEN_ARG]

    private val _uiState = MutableStateFlow<AcceptInviteUiState>(AcceptInviteUiState.EnterCode())
    val uiState: StateFlow<AcceptInviteUiState> = _uiState.asStateFlow()

    init {
        initialToken?.let { loadPreview(it) }
    }

    fun onCodeChanged(code: String) {
        val current = _uiState.value
        if (current is AcceptInviteUiState.EnterCode) {
            _uiState.value = current.copy(code = code)
        }
    }

    fun onSubmitCode() {
        val token = (_uiState.value as? AcceptInviteUiState.EnterCode)?.code?.trim()
        if (token.isNullOrEmpty()) return
        loadPreview(token)
    }

    /** Returns to [AcceptInviteUiState.EnterCode] — the retry action for every non-Preview state. */
    fun onRetry() {
        _uiState.value = AcceptInviteUiState.EnterCode()
    }

    fun onAccept() {
        val current = _uiState.value
        if (current !is AcceptInviteUiState.Preview) return
        viewModelScope.launch {
            _uiState.value = AcceptInviteUiState.Loading
            _uiState.value = when (householdRepository.joinWithToken(current.token)) {
                is HouseholdJoinOutcome.Joined -> AcceptInviteUiState.Joined
                HouseholdJoinOutcome.InvalidOrExpired -> AcceptInviteUiState.InvalidOrExpired
                HouseholdJoinOutcome.AlreadyInAHousehold -> AcceptInviteUiState.AlreadyInAHousehold
                HouseholdJoinOutcome.NetworkError ->
                    AcceptInviteUiState.Error("Couldn't join — check your connection")
            }
        }
    }

    private fun loadPreview(token: String) {
        _uiState.value = AcceptInviteUiState.Loading
        viewModelScope.launch {
            householdRepository.previewInvite(token)
                .onSuccess { preview -> _uiState.value = resolveAfterPreview(token, preview) }
                .onFailure { error -> _uiState.value = mapPreviewFailure(error) }
        }
    }

    /**
     * Both branches below are pre-checks so [onAccept] never has to: an anonymous session can't
     * accept at all, and a caller already in a household gets a locally-known outcome without a
     * network round trip — matching how the pending-invite inbox's own accept is written.
     */
    private suspend fun resolveAfterPreview(
        token: String,
        preview: HouseholdInvitePreview,
    ): AcceptInviteUiState = when {
        sessionManager.userSession.value !is UserSession.Authenticated -> AcceptInviteUiState.RequiresSignIn
        householdRepository.observeMyHousehold().first() != null -> AcceptInviteUiState.AlreadyInAHousehold
        else -> AcceptInviteUiState.Preview(preview, token)
    }

    private fun mapPreviewFailure(error: Throwable): AcceptInviteUiState =
        if ((error as? HouseholdApiException)?.statusCode == 404) {
            AcceptInviteUiState.InvalidOrExpired
        } else {
            AcceptInviteUiState.Error("Couldn't load that invite")
        }
}
