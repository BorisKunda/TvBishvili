package com.bk.mmovies.data

import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigServerException
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TMDB_API_KEY_REMOTE_CONFIG_PARAMETER = "tmdb_api_key_v3"
private const val FETCH_TIMEOUT_MILLIS = 10_000L
private const val HTTP_STATUS_TOO_MANY_REQUESTS = 429
private val HTTP_CLIENT_ERROR_STATUS_RANGE = 400..499

sealed interface RemoteApiKeyFetchResult {
    data class Fetched(val apiKey: String) : RemoteApiKeyFetchResult
    data object ConnectionFailed : RemoteApiKeyFetchResult
    data object KeyNotDelivered : RemoteApiKeyFetchResult
}

@Singleton
class RemoteApiKeyFetcher @Inject constructor() {

    suspend fun fetchApiKey(): RemoteApiKeyFetchResult {
        return try {
            withTimeoutOrNull(FETCH_TIMEOUT_MILLIS) {
                fetchFromRemoteConfig()
            } ?: RemoteApiKeyFetchResult.ConnectionFailed
        } catch (exception: Exception) {
            RemoteApiKeyFetchResult.ConnectionFailed
        }
    }

    private suspend fun fetchFromRemoteConfig(): RemoteApiKeyFetchResult {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 0
                }
                                           ).awaitOutcome()
        val fetchOutcome = remoteConfig.fetchAndActivate().awaitOutcome()
        if (!fetchOutcome.isSuccessful) {
            return classifyFetchFailure(fetchOutcome.failure)
        }
        val apiKey = remoteConfig.getString(TMDB_API_KEY_REMOTE_CONFIG_PARAMETER).trim()
        return if (apiKey.isEmpty()) {
            RemoteApiKeyFetchResult.KeyNotDelivered
        } else {
            RemoteApiKeyFetchResult.Fetched(apiKey)
        }
    }

    private fun classifyFetchFailure(failure: Exception?): RemoteApiKeyFetchResult {
        val wasRejectedByServer = failure is FirebaseRemoteConfigServerException &&
                failure.httpStatusCode in HTTP_CLIENT_ERROR_STATUS_RANGE &&
                failure.httpStatusCode != HTTP_STATUS_TOO_MANY_REQUESTS
        return if (wasRejectedByServer) {
            RemoteApiKeyFetchResult.KeyNotDelivered
        } else {
            RemoteApiKeyFetchResult.ConnectionFailed
        }
    }
}

private class TaskOutcome(val isSuccessful: Boolean, val failure: Exception?)

private suspend fun <T> Task<T>.awaitOutcome(): TaskOutcome {
    return suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            continuation.resume(TaskOutcome(task.isSuccessful, task.exception))
        }
    }
}
