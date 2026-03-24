package com.tenmilelabs.chefai.mealplans.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tenmilelabs.chefai.R
import com.tenmilelabs.chefai.auth.domain.SessionManager
import com.tenmilelabs.chefai.auth.domain.model.UserSession
import com.tenmilelabs.chefai.core.util.WhileUiSubscribed
import com.tenmilelabs.chefai.mealplans.domain.model.MealPlan
import com.tenmilelabs.chefai.mealplans.domain.repository.MealPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.inject.Inject

sealed interface MealPlansUiState {
    data object Loading : MealPlansUiState
    data class Success(val mealPlans: List<MealPlan>) : MealPlansUiState
    data object Error : MealPlansUiState
}

sealed interface MealPlansEvent {
    data class ShowSnackbar(val message: Int) : MealPlansEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MealPlansViewModel @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _uiEvent = MutableSharedFlow<MealPlansEvent>(replay = 1)
    val uiEvents: SharedFlow<MealPlansEvent> = _uiEvent.asSharedFlow()

    val uiState: StateFlow<MealPlansUiState> = sessionManager.userSession
        .flatMapLatest { session ->
            when (session) {
                is UserSession.Loading -> emptyFlow()
                is UserSession.Anonymous -> mealPlanRepository.observeMealPlansForUser(session.localUserId)
                is UserSession.Authenticated -> mealPlanRepository.observeMealPlansForUser(session.user.uuid)
            }
        }
        .map<List<MealPlan>, MealPlansUiState> { plans ->
            MealPlansUiState.Success(plans)
        }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(MealPlansUiState.Error)
        }
        .stateIn(
            scope = viewModelScope,
            started = WhileUiSubscribed,
            initialValue = MealPlansUiState.Loading
        )

    fun onDeleteMealPlan(uuid: UUID) {
        viewModelScope.launch {
            try {
                mealPlanRepository.deleteMealPlan(uuid)
                _uiEvent.emit(MealPlansEvent.ShowSnackbar(R.string.meal_plan_deleted))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiEvent.emit(MealPlansEvent.ShowSnackbar(R.string.meal_plan_error))
            }
        }
    }
}
