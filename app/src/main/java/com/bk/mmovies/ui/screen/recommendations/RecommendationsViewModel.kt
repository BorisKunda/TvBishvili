package com.bk.mmovies.ui.screen.recommendations

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bk.mmovies.core.R as CoreR
import com.bk.mmovies.data.GEMINI_API_KEY_REMOTE_CONFIG_PARAMETER
import com.bk.mmovies.data.RemoteApiKeyFetchResult
import com.bk.mmovies.data.RemoteApiKeyFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bk.mmovies.data.mapper.CatalogItemMapper
import com.bk.mmovies.data.source.local.preferences.RecommendationsDiskCacheSharedPrefs
import com.bk.mmovies.domain.model.CatalogItem
import com.bk.mmovies.domain.model.CatalogMediaType
import com.bk.mmovies.domain.model.RecommendationSuggestion
import com.bk.mmovies.domain.model.SearchResultMediaType
import com.bk.mmovies.domain.model.SearchResultModel
import com.bk.mmovies.domain.model.SeriesAirDateLabel
import com.bk.mmovies.domain.model.result.MoviesResult
import com.bk.mmovies.domain.model.result.RecommendationResult
import com.bk.mmovies.domain.model.result.SearchResult
import com.bk.mmovies.domain.model.result.ToggleFavoriteResult
import com.bk.mmovies.domain.model.result.TvSeriesResult
import com.bk.mmovies.domain.repository.AiRecommendationRepository
import com.bk.mmovies.domain.repository.AuthenticationRepository
import com.bk.mmovies.domain.repository.MovieRepository
import com.bk.mmovies.domain.repository.SearchRepository
import com.bk.mmovies.domain.repository.TvSeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private typealias FavoriteKey = Pair<Int, CatalogMediaType>

@HiltViewModel
class RecommendationsViewModel @Inject constructor(
        private val aiRecommendationRepository: AiRecommendationRepository,
        private val authenticationRepository: AuthenticationRepository,
        private val movieRepository: MovieRepository,
        private val tvRepository: TvSeriesRepository,
        private val searchRepository: SearchRepository,
        private val catalogItemMapper: CatalogItemMapper,
        private val recommendationsDiskCache: RecommendationsDiskCacheSharedPrefs,
        private val remoteApiKeyFetcher: RemoteApiKeyFetcher,
        @ApplicationContext private val context: Context
                                                  ) : ViewModel() {

    private val _screenState = MutableStateFlow<RecommendationsScreenState>(RecommendationsScreenState.Loading)
    val screenState: StateFlow<RecommendationsScreenState> = _screenState.asStateFlow()

    private val _messageEvent: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 1)
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    // Shown when a refresh failed but earlier picks are still on screen.
    private val _failureNotice = MutableSharedFlow<RecommendationsFailureNotice>(extraBufferCapacity = 1)
    val failureNotice: SharedFlow<RecommendationsFailureNotice> = _failureNotice.asSharedFlow()

    // After a failed load: the moment (epoch millis) to try again by itself. A
    // daily-quota failure waits for the quota reset; any other failure waits a
    // growing delay. The screen acts on it only while the tab is on screen.
    private val _autoRetryAtMillis = MutableStateFlow<Long?>(null)
    val autoRetryAtMillis: StateFlow<Long?> = _autoRetryAtMillis.asStateFlow()

    private var consecutiveFailures = 0

    private var loadJob: Job? = null
    private var hasTriedRemoteKeyAfterRejection = false

    private data class MemoryCache(
            val accountId: Int,
            val favoriteKeys: Set<FavoriteKey>,
            val items: List<CatalogItem>
                                  )

    private var memoryCache: MemoryCache? = null

    private sealed interface FavoritesOutcome {
        data class Loaded(val items: List<CatalogItem>) : FavoritesOutcome
        data class Failed(val message: String, val isConnectivityFailure: Boolean) : FavoritesOutcome
    }

    // An automatic retry leaves whatever is on screen (the picks, or the error
    // screen) untouched while it works and only changes it on success. The
    // shimmer is for the regular first load and for a manual Try again.
    fun autoRetry() = load(keepContentWhileLoading = true)

    fun load(keepContentWhileLoading: Boolean = false) {
        loadJob?.cancel()
        _autoRetryAtMillis.value = null
        val sessionId = authenticationRepository.getSharedPrefLoginSessionId()
        val accountId = authenticationRepository.getSharedPrefAccountId()
        if (sessionId == null || accountId == null) {
            _screenState.value = RecommendationsScreenState.LoginRequired
            return
        }
        val currentState = _screenState.value
        val keepCurrentScreen = keepContentWhileLoading &&
                (currentState is RecommendationsScreenState.Content || currentState is RecommendationsScreenState.Error)
        if (currentState !is RecommendationsScreenState.Content && !keepCurrentScreen) {
            _screenState.value = RecommendationsScreenState.Loading
        }
        loadJob = viewModelScope.launch {
            if (!aiRecommendationRepository.hasApiKey()) {
                when (val fetchResult = remoteApiKeyFetcher.fetchApiKey(GEMINI_API_KEY_REMOTE_CONFIG_PARAMETER)) {
                    is RemoteApiKeyFetchResult.Fetched       -> aiRecommendationRepository.saveSharedPrefApiKey(fetchResult.apiKey)
                    RemoteApiKeyFetchResult.ConnectionFailed -> {
                        _screenState.value = RecommendationsScreenState.Error(
                                context.getString(CoreR.string.error_ai_recommendations_failed),
                                isConnectivityFailure = true
                                                                             )
                        return@launch
                    }
                    RemoteApiKeyFetchResult.KeyNotDelivered  -> {
                        _screenState.value = RecommendationsScreenState.NeedsApiKey(missingKey = true)
                        return@launch
                    }
                }
            }
            if (!keepContentWhileLoading &&
                _screenState.value is RecommendationsScreenState.Content &&
                haveFavoritesChangedSinceLastResult(accountId)) {
                _screenState.value = RecommendationsScreenState.Loading
            }
            val favorites = when (val outcome = fetchFavorites(accountId, sessionId)) {
                is FavoritesOutcome.Loaded -> outcome.items
                is FavoritesOutcome.Failed -> {
                    if (_screenState.value !is RecommendationsScreenState.Content) {
                        _screenState.value = RecommendationsScreenState.Error(outcome.message, outcome.isConnectivityFailure)
                    } else {
                        _failureNotice.tryEmit(RecommendationsFailureNotice(outcome.message, offerTryAgain = true))
                    }
                    return@launch
                }
            }
            val favoriteKeys = favorites.map { it.id to it.mediaType }.toSet()

            val cached = memoryCache?.takeIf { it.accountId == accountId }
            if (cached != null && cached.favoriteKeys == favoriteKeys) {
                consecutiveFailures = 0
                showItems(cached.items)
                return@launch
            }

            val saved = recommendationsDiskCache.load(accountId)?.takeIf { it.items.isNotEmpty() }
            if (saved != null && saved.favoriteKeys == favoriteKeys) {
                consecutiveFailures = 0
                memoryCache = MemoryCache(accountId, favoriteKeys, saved.items)
                showItems(saved.items)
                return@launch
            }

            val staleItems = (cached?.items ?: saved?.items).orEmpty()
                    .filterNot { (it.id to it.mediaType) in favoriteKeys }
            if (!keepCurrentScreen) {
                _screenState.value = RecommendationsScreenState.Loading
            }

            when (val result = aiRecommendationRepository.getRecommendations(favorites)) {
                is RecommendationResult.Success -> {
                    hasTriedRemoteKeyAfterRejection = false
                    consecutiveFailures = 0
                    val items = resolveSuggestions(result.suggestions)
                            .filterNot { (it.id to it.mediaType) in favoriteKeys }
                    showItems(items)
                    if (items.isNotEmpty()) {
                        memoryCache = MemoryCache(accountId, favoriteKeys, items)
                        recommendationsDiskCache.save(accountId, favoriteKeys, items)
                    } else {
                        memoryCache = null
                    }
                }
                is RecommendationResult.Failure -> {
                    memoryCache = null
                    if (result.isInvalidApiKey) {
                        handleRejectedApiKey()
                        return@launch
                    }
                    val isFirstFailureInARow = consecutiveFailures == 0
                    consecutiveFailures++
                    _autoRetryAtMillis.value = result.retryAtEpochMillis
                            ?: (System.currentTimeMillis() + backoffBeforeAutoRetryMillis(consecutiveFailures))
                    if (staleItems.isEmpty()) {
                        _screenState.value = RecommendationsScreenState.Error(
                                result.errorMessage,
                                result.isConnectivityFailure
                                                                             )
                    } else {
                        showItems(staleItems)
                        // Only the first failure of a streak: later automatic
                        // retries fail quietly instead of re-announcing it.
                        if (isFirstFailureInARow) {
                            _failureNotice.tryEmit(
                                    RecommendationsFailureNotice(
                                            message = result.errorMessage,
                                            offerTryAgain = result.retryAtEpochMillis == null
                                                                )
                                                  )
                        }
                    }
                }
            }
        }
    }

    // Each automatic retry can cost up to one request per model against the
    // free quota, so the wait grows: 30 s, then 2 min, then every 5 min until
    // it succeeds or the user leaves the tab.
    private fun backoffBeforeAutoRetryMillis(failuresInARow: Int): Long = when (failuresInARow) {
        1    -> 30_000L
        2    -> 120_000L
        else -> 300_000L
    }

    // A key Google rejects may just be outdated in Remote Config's favor: one
    // re-fetch is tried before asking the user for their own key.
    private suspend fun handleRejectedApiKey() {
        val rejectedKey = aiRecommendationRepository.getSharedPrefApiKey()
        aiRecommendationRepository.removeSharedPrefApiKey()
        if (!hasTriedRemoteKeyAfterRejection) {
            hasTriedRemoteKeyAfterRejection = true
            val fetchResult = remoteApiKeyFetcher.fetchApiKey(GEMINI_API_KEY_REMOTE_CONFIG_PARAMETER)
            val replacementKey = (fetchResult as? RemoteApiKeyFetchResult.Fetched)?.apiKey
            if (replacementKey != null && replacementKey != rejectedKey) {
                aiRecommendationRepository.saveSharedPrefApiKey(replacementKey)
                load()
                return
            }
        }
        hasTriedRemoteKeyAfterRejection = false
        _messageEvent.tryEmit(context.getString(CoreR.string.toast_invalid_api_key))
        _screenState.value = RecommendationsScreenState.NeedsApiKey(missingKey = false)
    }

    private suspend fun haveFavoritesChangedSinceLastResult(accountId: Int): Boolean {
        val shownCache = memoryCache?.takeIf { it.accountId == accountId } ?: return false
        val currentKeys = movieRepository.getCachedFavoriteIds().map { it to CatalogMediaType.MOVIE }.toSet() +
                tvRepository.getCachedFavoriteIds().map { it to CatalogMediaType.TV_SERIES }.toSet()
        return currentKeys != shownCache.favoriteKeys
    }

    fun retry() {
        memoryCache = null
        load()
    }

    fun saveApiKey(apiKey: String) {
        aiRecommendationRepository.saveSharedPrefApiKey(apiKey)
        load()
    }

    suspend fun getTvSeriesAirDateLabel(seriesId: Int): SeriesAirDateLabel =
            tvRepository.getAirDateLabel(seriesId)

    fun onFavoriteClicked(catalogItem: CatalogItem) {
        val sessionId = authenticationRepository.getSharedPrefLoginSessionId() ?: return
        val accountId = authenticationRepository.getSharedPrefAccountId() ?: return
        val newIsFavorite = !catalogItem.isFavorite
        updateFavoriteState(catalogItem, newIsFavorite)
        viewModelScope.launch {
            val result = when (catalogItem.mediaType) {
                CatalogMediaType.MOVIE     -> movieRepository.toggleFavorite(accountId, sessionId, catalogItem.id, newIsFavorite)
                CatalogMediaType.TV_SERIES -> tvRepository.toggleFavorite(accountId, sessionId, catalogItem.id, newIsFavorite)
            }
            if (result is ToggleFavoriteResult.Failure) {
                updateFavoriteState(catalogItem, !newIsFavorite)
                _messageEvent.tryEmit(result.errorMessage)
            }
        }
    }

    private fun updateFavoriteState(catalogItem: CatalogItem, isFavorite: Boolean) {
        val current = _screenState.value as? RecommendationsScreenState.Content ?: return
        val updated = current.items.map {
            if (it.id == catalogItem.id && it.mediaType == catalogItem.mediaType) it.copy(isFavorite = isFavorite) else it
        }
        _screenState.value = current.copy(items = updated)
        memoryCache = memoryCache?.copy(items = updated)
    }

    private fun showItems(items: List<CatalogItem>) {
        _screenState.value = if (items.isEmpty()) {
            RecommendationsScreenState.Empty
        } else {
            RecommendationsScreenState.Content(items)
        }
    }

    private suspend fun fetchFavorites(accountId: Int, sessionId: String): FavoritesOutcome = coroutineScope {
        val moviesDeferred = async { movieRepository.getFavoriteMovies(accountId, sessionId) }
        val tvSeriesDeferred = async { tvRepository.getFavoriteTvSeries(accountId, sessionId) }
        val moviesResult = moviesDeferred.await()
        val tvSeriesResult = tvSeriesDeferred.await()
        when {
            moviesResult is MoviesResult.Failure    ->
                FavoritesOutcome.Failed(moviesResult.errorMessage, moviesResult.isConnectivityFailure)
            tvSeriesResult is TvSeriesResult.Failure ->
                FavoritesOutcome.Failed(tvSeriesResult.errorMessage, tvSeriesResult.isConnectivityFailure)
            moviesResult is MoviesResult.Success && tvSeriesResult is TvSeriesResult.Success ->
                FavoritesOutcome.Loaded(
                        catalogItemMapper.fromMovies(moviesResult.movies) + catalogItemMapper.fromTvSeries(tvSeriesResult.tvSeries)
                                       )
            else                                     -> FavoritesOutcome.Loaded(emptyList())
        }
    }

    private suspend fun resolveSuggestions(suggestions: List<RecommendationSuggestion>): List<CatalogItem> = coroutineScope {
        suggestions.map { suggestion ->
            async {
                val candidates = when (val result = searchRepository.search(suggestion.query)) {
                    is SearchResult.Success -> result.results.mapNotNull { it.toCatalogItemOrNull() }
                    is SearchResult.Failure -> emptyList()
                }
                suggestion.mediaTypeHint?.let { hint -> candidates.firstOrNull { it.mediaType == hint } }
                        ?: candidates.firstOrNull()
            }
        }.awaitAll().filterNotNull().distinctBy { it.id to it.mediaType }.withFavoriteFlags()
    }

    private suspend fun List<CatalogItem>.withFavoriteFlags(): List<CatalogItem> {
        if (isEmpty()) return this
        val movieFavoriteIds = movieRepository.getCachedFavoriteIds()
        val tvFavoriteIds = tvRepository.getCachedFavoriteIds()
        return map {
            val favoriteIds = if (it.mediaType == CatalogMediaType.MOVIE) movieFavoriteIds else tvFavoriteIds
            it.copy(isFavorite = favoriteIds.contains(it.id))
        }
    }

    private fun SearchResultModel.toCatalogItemOrNull(): CatalogItem? {
        val catalogMediaType = when (mediaType) {
            SearchResultMediaType.MOVIE     -> CatalogMediaType.MOVIE
            SearchResultMediaType.TV_SERIES -> CatalogMediaType.TV_SERIES
            SearchResultMediaType.PERSON    -> return null
        }
        return CatalogItem(
                id = id,
                title = title,
                imageUrl = imageUrl,
                backdropUrl = backdropUrl,
                releaseDate = subtitle,
                mediaType = catalogMediaType,
                rating = rating
                          )
    }
}

data class RecommendationsFailureNotice(val message: String, val offerTryAgain: Boolean)

sealed interface RecommendationsScreenState {
    data object Loading : RecommendationsScreenState
    data object LoginRequired : RecommendationsScreenState
    data class NeedsApiKey(val missingKey: Boolean) : RecommendationsScreenState
    data object Empty : RecommendationsScreenState
    data class Error(val errorMessage: String, val isConnectivityFailure: Boolean = false) : RecommendationsScreenState
    data class Content(val items: List<CatalogItem>) : RecommendationsScreenState
}
