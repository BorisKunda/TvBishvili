package com.bk.mmovies.ui.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bk.mmovies.connectivity.InternetMonitor
import com.bk.mmovies.domain.model.PersonDetailsModel
import com.bk.mmovies.domain.model.result.PersonDetailsResult
import com.bk.mmovies.domain.repository.PersonRepository
import com.bk.mmovies.locale.LocaleMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActorDetailsViewModel @Inject constructor(
        private val personRepository: PersonRepository,
        localeMonitor: LocaleMonitor,
        internetMonitor: InternetMonitor
                                                ) : ViewModel() {

    private val _personDetailsState = MutableStateFlow<PersonDetailsUiState>(
            PersonDetailsUiState.Loading
                                                                             )
    val personDetailsState: StateFlow<PersonDetailsUiState> = _personDetailsState.asStateFlow()

    private var personId: Int? = null
    private var loadPersonDetailsJob: Job? = null

    init {
        // This ViewModel is scoped to the screen's NavBackStackEntry, so it
        // outlives a language switch — without this, reopening the same
        // actor after changing language just replays the previous-language
        // result instead of refetching it (e.g. place_of_birth from TMDB
        // is locale-dependent, so a stale fetch shows the wrong language).
        viewModelScope.launch {
            localeMonitor.currentLanguage
                    .drop(1)
                    .collectLatest {
                        val id = personId ?: return@collectLatest
                        fetchPersonDetails(id)
                    }
        }
        viewModelScope.launch {
            internetMonitor.isInternetAvailable
                    .drop(1)
                    .collectLatest { isAvailable ->
                        val currentState = _personDetailsState.value
                        val isShowingConnectionError = currentState is PersonDetailsUiState.Error &&
                                currentState.isConnectivityFailure
                        if (isAvailable && isShowingConnectionError) {
                            retry()
                        }
                    }
        }
    }

    fun loadPersonDetails(personId: Int) {
        // This ViewModel is scoped to the NavBackStackEntry, so it outlives the
        // dialog that opened it. Skipping the refetch for an already-loaded
        // person avoids a redundant call, but a previous *failure* must not be
        // sticky — otherwise reopening the same actor replays the stale error
        // with no way back.
        if (this.personId == personId && _personDetailsState.value !is PersonDetailsUiState.Error) return
        this.personId = personId
        fetchPersonDetails(personId)
    }

    fun retry() {
        val personId = personId ?: return
        fetchPersonDetails(personId)
    }

    private fun fetchPersonDetails(personId: Int) {
        _personDetailsState.value = PersonDetailsUiState.Loading
        loadPersonDetailsJob?.cancel()
        loadPersonDetailsJob = viewModelScope.launch {
            when (val result = personRepository.getPersonDetails(personId)) {
                is PersonDetailsResult.Success -> {
                    _personDetailsState.value = PersonDetailsUiState.Content(result.personDetails)
                }
                is PersonDetailsResult.Failure -> {
                    _personDetailsState.value = PersonDetailsUiState.Error(result.errorMessage, result.isConnectivityFailure)
                }
            }
        }
    }
}

sealed interface PersonDetailsUiState {
    object Loading : PersonDetailsUiState
    data class Content(val personDetails: PersonDetailsModel) : PersonDetailsUiState
    data class Error(val errorMessage: String, val isConnectivityFailure: Boolean = false) : PersonDetailsUiState
}
