package com.bk.mmovies.data.source.remote.interceptor

import com.bk.mmovies.data.source.local.preferences.AuthCredentialsSharedPrefs
import com.bk.mmovies.data.source.remote.AUTHENTICATION_ENDPOINT
import com.bk.mmovies.data.source.remote.QUERY_PARAM_API_KEY
import com.bk.mmovies.data.source.remote.dto.ErrorDto
import com.bk.mmovies.data.source.remote.dto.indicatesRejectedApiKey
import com.bk.mmovies.data.source.remote.keyrefresh.ApiKeyRefresher
import com.bk.mmovies.data.source.remote.keyrefresh.ApiKeyRefresherHolder
import com.bk.mmovies.data.source.remote.keyrefresh.ApiKeyRejectionEvents
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_UNAUTHORIZED = 401
private const val MAX_ERROR_BODY_BYTES = 4096L

@Singleton
class ApiKeyRejectionInterceptor @Inject constructor(
        private val apiKeyStorage: AuthCredentialsSharedPrefs,
        private val refresherHolder: ApiKeyRefresherHolder,
        private val rejectionEvents: ApiKeyRejectionEvents,
        private val gson: Gson
                                                    ) : Interceptor {

    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (isApiKeyValidationRequest(request) || !isApiKeyRejection(response)) {
            return response
        }
        val refresher = refresherHolder.refresher ?: return response
        val rejectedKey = request.url.queryParameter(QUERY_PARAM_API_KEY) ?: return response
        val replacementKey = obtainReplacementKey(refresher, rejectedKey)
        if (replacementKey == null) {
            discardRejectedKeyAndNotify()
            return response
        }
        response.close()
        val retryRequest = request.newBuilder()
                .url(
                        request.url.newBuilder()
                                .setQueryParameter(QUERY_PARAM_API_KEY, replacementKey)
                                .build()
                    )
                .build()
        val retryResponse = chain.proceed(retryRequest)
        if (isApiKeyRejection(retryResponse)) {
            discardRejectedKeyAndNotify()
        }
        return retryResponse
    }

    private fun discardRejectedKeyAndNotify() {
        apiKeyStorage.removeApiKey()
        rejectionEvents.notifyRejected()
    }

    private fun isApiKeyValidationRequest(request: Request): Boolean {
        return request.url.encodedPath.endsWith("/$AUTHENTICATION_ENDPOINT")
    }

    private fun isApiKeyRejection(response: Response): Boolean {
        if (response.code != HTTP_UNAUTHORIZED) return false
        val errorBodyText = response.peekBody(MAX_ERROR_BODY_BYTES).string()
        val errorDto: ErrorDto? = try {
            gson.fromJson(errorBodyText, ErrorDto::class.java)
        } catch (exception: Exception) {
            null
        }
        return errorDto.indicatesRejectedApiKey
    }

    private fun obtainReplacementKey(
            refresher: ApiKeyRefresher,
            rejectedKey: String
                                    ): String? {
        synchronized(refreshLock) {
            val currentKey = apiKeyStorage.getSharedPrefApiKey() ?: return null
            if (currentKey != rejectedKey) {
                return currentKey
            }
            val fetchedKey = runBlocking { refresher.fetchReplacementApiKey() } ?: return null
            if (fetchedKey == rejectedKey) return null
            apiKeyStorage.saveSharedPrefApiKey(fetchedKey)
            return fetchedKey
        }
    }
}
