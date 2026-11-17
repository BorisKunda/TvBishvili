package com.bk.mmovies.ui.screen.recommendations

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bk.mmovies.R
import com.bk.mmovies.connectivity.openWebPage
import com.bk.mmovies.core.R as CoreR
import com.bk.mmovies.data.source.remote.GOOGLE_AI_STUDIO_WEBPAGE_API_KEYS
import com.bk.mmovies.data.source.remote.GOOGLE_AI_STUDIO_WEBPAGE_GETTING_STARTED
import com.bk.mmovies.domain.model.CatalogMediaType
import com.bk.mmovies.domain.model.Category
import com.bk.mmovies.domain.model.MovieCategory
import com.bk.mmovies.domain.model.TvSeriesCategory
import com.bk.mmovies.ui.component.EmptyStateView
import com.bk.mmovies.ui.component.InvalidApiKeyView
import com.bk.mmovies.ui.component.LoadErrorScreen
import com.bk.mmovies.ui.screen.catalog.screencomponents.CatalogListView
import com.bk.mmovies.ui.screen.catalog.screencomponents.AiRecommendationsLoadingPlaceholderList
import kotlinx.coroutines.delay

// Wall-clock polling rather than one long delay, so a phone that slept through
// the reset still loads again soon after it.
private const val AUTO_RETRY_POLL_MILLIS = 30_000L
private const val AUTO_RETRY_MARGIN_MILLIS = 5_000L

@Composable
fun RecommendationsScreen(
        onNavigateToDetailsScreen: (id: Int, category: Category) -> Unit,
        onLoginClicked: () -> Unit,
        viewModel: RecommendationsViewModel = hiltViewModel()
                         ) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.load()
    }
    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val tryAgainLabel = stringResource(R.string.try_again)
    LaunchedEffect(Unit) {
        viewModel.failureNotice.collect { notice ->
            val snackbarResult = snackbarHostState.showSnackbar(
                    message = notice.message,
                    actionLabel = if (notice.offerTryAgain) tryAgainLabel else null,
                    duration = if (notice.offerTryAgain) SnackbarDuration.Long else SnackbarDuration.Short
                                                               )
            if (snackbarResult == SnackbarResult.ActionPerformed) viewModel.retry()
        }
    }

    // Only runs while this tab is on screen: leaving it cancels the wait.
    val autoRetryAtMillis by viewModel.autoRetryAtMillis.collectAsStateWithLifecycle()
    LaunchedEffect(autoRetryAtMillis) {
        val retryAtMillis = autoRetryAtMillis ?: return@LaunchedEffect
        while (true) {
            val remainingMillis = retryAtMillis + AUTO_RETRY_MARGIN_MILLIS - System.currentTimeMillis()
            if (remainingMillis <= 0) break
            delay(remainingMillis.coerceAtMost(AUTO_RETRY_POLL_MILLIS))
        }
        viewModel.autoRetry()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    when (val state = screenState) {
        is RecommendationsScreenState.Loading       -> AiRecommendationsLoadingPlaceholderList()

        is RecommendationsScreenState.LoginRequired -> EmptyStateView(
                imageResId = CoreR.drawable.ic_ai,
                title = stringResource(R.string.ai_recommendations_title),
                message = stringResource(R.string.ai_recommendations_login_required),
                actionLabel = stringResource(R.string.log_in_action),
                onActionClick = onLoginClicked
                                                                     )

        is RecommendationsScreenState.NeedsApiKey   -> InvalidApiKeyView(
                onOpenTmdbSettingsClicked = { openWebPage(context, GOOGLE_AI_STUDIO_WEBPAGE_API_KEYS) },
                onApiKeyHelpClicked = { openWebPage(context, GOOGLE_AI_STUDIO_WEBPAGE_GETTING_STARTED) },
                onSaveClicked = viewModel::saveApiKey,
                missingKey = state.missingKey,
                openSettingsLabel = stringResource(R.string.open_google_ai_studio),
                isEmbedded = true
                                                         )

        is RecommendationsScreenState.Empty         -> EmptyStateView(
                imageResId = CoreR.drawable.ic_ai,
                title = stringResource(R.string.ai_recommendations_title),
                message = stringResource(R.string.ai_recommendations_no_signal_message)
                                                                     )

        is RecommendationsScreenState.Error         -> LoadErrorScreen(
                errorMessageText = state.errorMessage,
                isConnectivityFailure = state.isConnectivityFailure,
                onTryAgainClicked = viewModel::retry
                                                                      )

        is RecommendationsScreenState.Content       -> CatalogListView(
                catalogItems = state.items,
                selectedCategory = MovieCategory.PopularMovieCategory,
                onCatalogItemClicked = { id ->
                    val item = state.items.firstOrNull { it.id == id }
                    val category: Category = if (item?.mediaType == CatalogMediaType.TV_SERIES) {
                        TvSeriesCategory.PopularTvSeriesCategory
                    } else {
                        MovieCategory.PopularMovieCategory
                    }
                    onNavigateToDetailsScreen(id, category)
                },
                isGuest = false,
                onFavoriteClicked = viewModel::onFavoriteClicked,
                getTvSeriesAirDateLabel = viewModel::getTvSeriesAirDateLabel
                                                                     )
    }
        SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
                    )
    }
}
