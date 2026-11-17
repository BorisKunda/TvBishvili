package com.bk.mmovies.domain.model.result

import com.bk.mmovies.domain.model.RecommendationSuggestion

sealed interface RecommendationResult {
    data class Success(val suggestions: List<RecommendationSuggestion>) : RecommendationResult
    data class Failure(
            val errorMessage: String,
            val isConnectivityFailure: Boolean = false,
            val isInvalidApiKey: Boolean = false,
            // Set only when every model hit its daily quota: the moment (epoch
            // millis) the quotas reset, so a caller can load again by itself.
            val retryAtEpochMillis: Long? = null
                      ) : RecommendationResult
}
