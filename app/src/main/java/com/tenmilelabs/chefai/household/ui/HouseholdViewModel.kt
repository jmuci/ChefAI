package com.tenmilelabs.chefai.household.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.core.domain.model.HouseholdRole
import com.tenmilelabs.chefai.household.domain.model.Household
import com.tenmilelabs.chefai.household.domain.model.HouseholdInvite
import com.tenmilelabs.chefai.household.domain.model.HouseholdInviteLink
import com.tenmilelabs.chefai.household.domain.model.HouseholdJoinOutcome
import com.tenmilelabs.chefai.household.domain.model.PendingHouseholdInvite
import com.tenmilelabs.chefai.household.domain.repository.HouseholdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

sealed interface HouseholdUiState {
    data object Loading : HouseholdUiState
    data object NoHousehold : HouseholdUiState
    data class Success(
        val household: Household,
        val myRole: HouseholdRole,
        val currentUserId: UUID?,
        val isRefreshing: Boolean,
    ) : HouseholdUiState
    data class Error(val message: String) : HouseholdUiState
}

sealed interface HouseholdEvent {
    data class ShowError(val message: String) : HouseholdEvent
    data class ShareInviteLink(val link: HouseholdInviteLink) : HouseholdEvent
    data object InviteSent : HouseholdEvent
    data object LeftHousehold : HouseholdEvent
    data object PendingInviteResolved : HouseholdEvent
}

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _events = MutableSharedFlow<HouseholdEvent>()
    val events: SharedFlow<HouseholdEvent> = _events.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)

    /** Set only by a [refresh] that fails with nothing cached — see [uiState]'s derivation. */
    private val _lastRefreshError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HouseholdUiState> = combine(
        householdRepository.observeMyHousehold(),
        _isRefreshing,
        _lastRefreshError,
    ) { household, isRefreshing, lastRefreshError ->
        when {
            household != null -> HouseholdUiState.Success(
                household = household,
                myRole = myRoleIn(household),
                currentUserId = sessionManager.getCurrentUserId(),
                isRefreshing = isRefreshing,
            )
            // Only surfaced while there's nothing cached to fall back on — once a household is
            // cached, a background refresh failure leaves it visible (possibly stale) rather than
            // replacing it with an error screen.
            lastRefreshError != null -> HouseholdUiState.Error(lastRefreshError)
            else -> HouseholdUiState.NoHousehold
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HouseholdUiState.Loading,
    )

    /**
     * The signed-in owner's own outstanding invites — screen 11's "Pending invites" group
     * (email + expiry + Revoke). Distinct from [pendingInvites], which is the invitee's-eye view
     * of invites addressed *to* this caller. Not cached server-side, so this is a plain
     * one-shot-per-refresh list rather than a cold [Flow] like [uiState].
     */
    private val _outstandingInvites = MutableStateFlow<List<HouseholdInvite>>(emptyList())
    val outstandingInvites: StateFlow<List<HouseholdInvite>> = _outstandingInvites.asStateFlow()

    /**
     * In-app invites addressed to this user — shown in [HouseholdUiState.NoHousehold]'s pending
     * invite inbox. Kept separate from [uiState] rather than folded into [HouseholdUiState.NoHousehold]
     * as a field, since a household member (a [HouseholdUiState.Success] session) can't have any —
     * accepting one always means leaving whatever household they're currently in first, which this
     * screen doesn't offer.
     */
    val pendingInvites: StateFlow<List<PendingHouseholdInvite>> = householdRepository
        .observePendingInvites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        refresh()
    }

    /**
     * Every household this device caches includes the caller as a member by construction (the
     * server never returns one otherwise) — MEMBER is a fail-closed fallback for a theoretically
     * stale cache, not an expected path. The client cache is never a security boundary anyway: the
     * server re-authorizes every mutation regardless of this value.
     */
    private fun myRoleIn(household: Household): HouseholdRole =
        household.members
            .firstOrNull { it.userId == sessionManager.getCurrentUserId() }
            ?.role
            ?: HouseholdRole.MEMBER

    fun onRefresh() = refresh()

    private fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            householdRepository.refresh()
                .onSuccess { _lastRefreshError.value = null }
                .onFailure {
                    if (it is CancellationException) throw it
                    Timber.e(it, "refresh: failed")
                    _lastRefreshError.value = "Couldn't load your household"
                }
            _isRefreshing.value = false
            refreshOutstandingInvites()
        }
    }

    /**
     * Owner-only outstanding invites (screen 11's "Pending invites" group) — reloaded after every
     * [refresh] and after any action that changes the set: sending an invite or revoking one.
     * Silently cleared for a member or once there's no household at all, rather than surfaced as
     * an error, since it is supplementary to the household itself.
     */
    private suspend fun refreshOutstandingInvites() {
        val household = householdRepository.observeMyHousehold().first()
        if (household == null || myRoleIn(household) != HouseholdRole.OWNER) {
            _outstandingInvites.value = emptyList()
            return
        }
        householdRepository.listOutstandingInvites()
            .onSuccess { _outstandingInvites.value = it }
            .onFailure {
                if (it is CancellationException) throw it
                Timber.e(it, "refreshOutstandingInvites: failed")
            }
    }

    fun onCreateHousehold(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            householdRepository.createHousehold(trimmed).onFailure {
                if (it is CancellationException) throw it
                Timber.e(it, "onCreateHousehold: failed")
                _events.emit(HouseholdEvent.ShowError("Couldn't create the household"))
            }
        }
    }

    fun onInviteByLink() {
        viewModelScope.launch {
            householdRepository.createInviteLink()
                .onSuccess {
                    _events.emit(HouseholdEvent.ShareInviteLink(it))
                    refreshOutstandingInvites()
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    Timber.e(it, "onInviteByLink: failed")
                    _events.emit(HouseholdEvent.ShowError("Couldn't create an invite link"))
                }
        }
    }

    fun onInviteByEmail(email: String) {
        viewModelScope.launch {
            householdRepository.inviteByEmail(email)
                .onSuccess {
                    _events.emit(HouseholdEvent.InviteSent)
                    refreshOutstandingInvites()
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    Timber.e(it, "onInviteByEmail: failed")
                    _events.emit(HouseholdEvent.ShowError("Couldn't send that invite"))
                }
        }
    }

    fun onRevokeInvite(inviteId: UUID) {
        viewModelScope.launch {
            householdRepository.revokeInvite(inviteId)
                .onSuccess { refreshOutstandingInvites() }
                .onFailure {
                    if (it is CancellationException) throw it
                    Timber.e(it, "onRevokeInvite: failed for $inviteId")
                    _events.emit(HouseholdEvent.ShowError("Couldn't revoke that invite"))
                }
        }
    }

    fun onRemoveMember(userId: UUID) {
        viewModelScope.launch {
            // Read the household fresh rather than trust `uiState.value` — that's only kept live
            // while something is actually collecting it (`WhileSubscribed`), which this call has no
            // guarantee of.
            val household = householdRepository.observeMyHousehold().first() ?: return@launch
            if (myRoleIn(household) != HouseholdRole.OWNER) return@launch

            householdRepository.removeMember(userId).onFailure {
                if (it is CancellationException) throw it
                Timber.e(it, "onRemoveMember: failed for $userId")
                _events.emit(HouseholdEvent.ShowError("Couldn't remove that member"))
            }
        }
    }

    fun onLeaveHousehold() {
        viewModelScope.launch {
            householdRepository.leaveHousehold()
                .onSuccess { _events.emit(HouseholdEvent.LeftHousehold) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Timber.e(it, "onLeaveHousehold: failed")
                    _events.emit(HouseholdEvent.ShowError("Couldn't leave the household"))
                }
        }
    }

    fun onAcceptPendingInvite(inviteId: UUID) {
        viewModelScope.launch {
            when (val outcome = householdRepository.acceptInvite(inviteId)) {
                is HouseholdJoinOutcome.Joined -> _events.emit(HouseholdEvent.PendingInviteResolved)
                HouseholdJoinOutcome.InvalidOrExpired ->
                    _events.emit(HouseholdEvent.ShowError("That invite is no longer valid"))
                HouseholdJoinOutcome.AlreadyInAHousehold ->
                    _events.emit(HouseholdEvent.ShowError("You're already in a household"))
                HouseholdJoinOutcome.NetworkError ->
                    _events.emit(HouseholdEvent.ShowError("Couldn't accept that invite — check your connection"))
            }
        }
    }

    fun onDeclinePendingInvite(inviteId: UUID) {
        viewModelScope.launch {
            householdRepository.declineInvite(inviteId)
                .onSuccess { _events.emit(HouseholdEvent.PendingInviteResolved) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Timber.e(it, "onDeclinePendingInvite: failed for $inviteId")
                    _events.emit(HouseholdEvent.ShowError("Couldn't decline that invite"))
                }
        }
    }
}
