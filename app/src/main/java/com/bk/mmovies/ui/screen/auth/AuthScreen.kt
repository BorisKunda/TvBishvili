package com.bk.mmovies.ui.screen.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bk.mmovies.connectivity.openWebPage
import com.bk.mmovies.core.R as CoreR
import com.bk.mmovies.data.source.remote.TMDB_WEBPAGE_SIGNUP_URL
import com.bk.mmovies.ui.component.LoaderView
import com.bk.mmovies.ui.screen.auth.screencomponents.AuthWelcomeScreen
import com.bk.mmovies.util.logDebug

private const val TAG = "AuthScreen"


@Composable
fun AuthScreen(
        onNavigateToMoviesScreen: () -> Unit,
        onNavigateToTerms: () -> Unit = {}
              ) {
    val context = LocalContext.current
    val authViewModel = hiltViewModel<AuthViewModel>()
    val state by authViewModel.authScreenState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarDismissLabel = stringResource(CoreR.string.snackbar_dismiss_ok)
    var credentialsFormResetCount by remember { mutableIntStateOf(0) }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state) {
        if (state is AuthScreenState.Error || state is AuthScreenState.LoginError) {
            softwareKeyboardController?.hide()
            focusManager.clearFocus()
        }
        when (val currentState = state) {
            is AuthScreenState.Error        -> snackbarHostState.showSnackbar(
                    message = currentState.errorMessage,
                    actionLabel = snackbarDismissLabel,
                    duration = SnackbarDuration.Indefinite
                                                                               )
            is AuthScreenState.LoginError   -> {
                snackbarHostState.showSnackbar(
                        message = currentState.errorMessage,
                        actionLabel = snackbarDismissLabel,
                        duration = SnackbarDuration.Indefinite
                                              )
                credentialsFormResetCount++
                authViewModel.onCredentialsEdited()
            }
            AuthScreenState.Loading         -> snackbarHostState.currentSnackbarData?.dismiss()
            AuthScreenState.Unauthenticated -> Unit
        }
    }
    BackHandler(enabled = snackbarHostState.currentSnackbarData != null) {
        snackbarHostState.currentSnackbarData?.dismiss()
    }
    Box(modifier = Modifier.fillMaxSize()) {
    when (val currentState = state) {
        AuthScreenState.Unauthenticated, is AuthScreenState.Error, is AuthScreenState.LoginError -> {
            val loginErrorState = currentState as? AuthScreenState.LoginError
            AuthWelcomeScreen(
                    credentialsResetSignal = credentialsFormResetCount,
                    initialUsername = loginErrorState?.attemptedUsername.orEmpty(),
                    initialPassword = loginErrorState?.attemptedPassword.orEmpty(),
                    isLoginError = loginErrorState != null,
                    onCredentialsEdited = { authViewModel.onCredentialsEdited() },
                    onLoginClicked = { username, password ->
                        authViewModel.onLoginClicked(username, password)
                    },
                    onRegisterOnTmdbClicked = {
                        openWebPage(
                                context,
                                TMDB_WEBPAGE_SIGNUP_URL
                                   )
                    },
                    onContinueAsGuestClicked = {
                        authViewModel.onContinueAsGuestClicked()
                    },
                    onNavigateToTerms = onNavigateToTerms,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                             )
        }
        AuthScreenState.Loading                                   -> {
            LoaderView()
        }

    }
    }
    LaunchedEffect(Unit) {
        authViewModel.goToMoviesNavEvent.collect {
            onNavigateToMoviesScreen()
        }
    }

    DisposableEffect(Unit) {
        logDebug(
                TAG,
                "Composed"
                )
        onDispose {
            logDebug(
                    TAG,
                    "Disposed"
                    )
        }
    }
}







