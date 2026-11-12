package com.bk.mmovies.ui.screen.splash

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bk.mmovies.connectivity.InternetMonitor
import com.bk.mmovies.data.RemoteApiKeyFetchResult
import com.bk.mmovies.data.RemoteApiKeyFetcher
import com.bk.mmovies.domain.model.result.ApiKeyValidationResult
import com.bk.mmovies.domain.repository.AuthenticationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

// Keeps the logo on screen long enough to read rather than flashing past on a
// warm start with a cached session.
private val SPLASH_MINIMUM_VISIBLE_DURATION = 1000.milliseconds
private val NETWORK_SETTLE_DELAY = 1500.milliseconds
private const val MAX_NETWORK_CHANGE_RETRIES = 2

@HiltViewModel
class SplashViewModel @Inject constructor(
        private val authenticationRepository: AuthenticationRepository,
        private val remoteApiKeyFetcher: RemoteApiKeyFetcher,
        private val internetMonitor: InternetMonitor
                                         ) : ViewModel() {

    private val _screenState =
            MutableStateFlow<SplashScreenState>(
                    SplashScreenState.Loading
                                               )

    val screenState: StateFlow<SplashScreenState> =
            _screenState.asStateFlow()

    private val _goToAuthEvent =
            MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1
                                   )

    val goToAuthEvent: SharedFlow<Unit> =
            _goToAuthEvent.asSharedFlow()

    private val _goToMoviesEvent =
            MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1
                                   )

    val goToMoviesEvent: SharedFlow<Unit> =
            _goToMoviesEvent.asSharedFlow()

    private val _invalidApiKeyToastEvent =
            MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1
                                   )

    val invalidApiKeyToastEvent: SharedFlow<Unit> =
            _invalidApiKeyToastEvent.asSharedFlow()

    // Latched once a navigation event has been emitted; see loadSplashData().
    private var hasNavigatedAway = false

    init {
        viewModelScope.launch {
            loadSplashData()
        }
        viewModelScope.launch {
            internetMonitor.isInternetAvailable.collectLatest { isAvailable ->
                if (isAvailable && _screenState.value == SplashScreenState.Offline) {
                    retry()
                }
            }
        }
        viewModelScope.launch {
            internetMonitor.networkChanges.collectLatest {
                delay(NETWORK_SETTLE_DELAY)
                if (_screenState.value == SplashScreenState.ServerUnreachable) {
                    retry()
                }
            }
        }
    }

    fun retry() {
        networkChangeRetryCount = 0
        viewModelScope.launch {
            loadSplashData()
        }
    }

    private var networkChangeRetryCount = 0

    private fun shouldRetryAfterNetworkChange(attemptStartedAtMillis: Long): Boolean {
        if (networkChangeRetryCount >= MAX_NETWORK_CHANGE_RETRIES) return false
        if (internetMonitor.lastNetworkChangeElapsedMillis < attemptStartedAtMillis) return false
        networkChangeRetryCount++
        return true
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            _screenState.value = SplashScreenState.Loading
            authenticationRepository.saveSharedPrefApiKey(apiKey)
            when (authenticationRepository.getApiKeyValidationResult()) {
                is ApiKeyValidationResult.Failure -> {
                    authenticationRepository.removeSharedPrefApiKey()
                    _invalidApiKeyToastEvent.emit(Unit)
                    _screenState.value = SplashScreenState.MissingApiKey
                }
                ApiKeyValidationResult.Success    -> {
                    loadSplashData()
                }
            }
        }
    }

    private fun connectionProblemReadiness(): ApiKeyReadiness {
        return if (internetMonitor.isInternetAvailable.value) {
            ApiKeyReadiness.ServerUnreachable
        } else {
            ApiKeyReadiness.Offline
        }
    }

    private suspend fun restoreApiKeyFromRemoteConfig(): ApiKeyReadiness {
        val remoteApiKey = when (val fetchResult = remoteApiKeyFetcher.fetchApiKey()) {
            is RemoteApiKeyFetchResult.Fetched         -> fetchResult.apiKey
            RemoteApiKeyFetchResult.ConnectionFailed   -> return connectionProblemReadiness()
            RemoteApiKeyFetchResult.KeyNotDelivered    -> return ApiKeyReadiness.NeedsManualEntry
        }
        authenticationRepository.saveSharedPrefApiKey(remoteApiKey)
        return when (val validationResult = authenticationRepository.getApiKeyValidationResult()) {
            is ApiKeyValidationResult.Failure -> {
                authenticationRepository.removeSharedPrefApiKey()
                if (validationResult.isConnectivityFailure) {
                    connectionProblemReadiness()
                } else {
                    ApiKeyReadiness.NeedsManualEntry
                }
            }
            ApiKeyValidationResult.Success    -> ApiKeyReadiness.Ready
        }
    }

    private suspend fun ensureApiKeyReadiness(): ApiKeyReadiness {
        if (authenticationRepository.getSharedPrefApiKey() == null) {
            return restoreApiKeyFromRemoteConfig()
        }
        return ApiKeyReadiness.Ready
    }

    private suspend fun loadSplashData() {
        val attemptStartedAtMillis = SystemClock.elapsedRealtime()
        _screenState.value = SplashScreenState.Loading
        delay(SPLASH_MINIMUM_VISIBLE_DURATION)
        when (ensureApiKeyReadiness()) {
            ApiKeyReadiness.Offline          -> {
                _screenState.value = SplashScreenState.Offline
                return
            }
            ApiKeyReadiness.ServerUnreachable -> {
                if (shouldRetryAfterNetworkChange(attemptStartedAtMillis)) {
                    delay(NETWORK_SETTLE_DELAY)
                    loadSplashData()
                    return
                }
                _screenState.value = SplashScreenState.ServerUnreachable
                return
            }
            ApiKeyReadiness.NeedsManualEntry -> {
                _screenState.value = SplashScreenState.MissingApiKey
                return
            }
            ApiKeyReadiness.Ready            -> Unit
        }
        // saveApiKey() can call this a second time after init's call already
        // navigated away (e.g. a slow validation response landing late) -
        // without a latch that would fire a second navigate() and push a
        // duplicate destination onto the back stack.
        if (hasNavigatedAway) return
        hasNavigatedAway = true
        if (authenticationRepository.getSharedPrefLoginSessionId() != null) {
            _goToMoviesEvent.emit(Unit)
        } else {
            _goToAuthEvent.emit(Unit)
        }
    }
}

private enum class ApiKeyReadiness {
    Ready,
    Offline,
    ServerUnreachable,
    NeedsManualEntry
}

sealed interface SplashScreenState {
    data object Loading : SplashScreenState
    data object Offline : SplashScreenState
    data object ServerUnreachable : SplashScreenState
    data object MissingApiKey : SplashScreenState
}