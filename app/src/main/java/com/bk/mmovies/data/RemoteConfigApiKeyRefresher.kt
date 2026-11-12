package com.bk.mmovies.data

import com.bk.mmovies.data.source.remote.keyrefresh.ApiKeyRefresher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigApiKeyRefresher @Inject constructor(
        private val remoteApiKeyFetcher: RemoteApiKeyFetcher
                                                     ) : ApiKeyRefresher {

    override suspend fun fetchReplacementApiKey(): String? {
        val fetchResult = remoteApiKeyFetcher.fetchApiKey()
        return (fetchResult as? RemoteApiKeyFetchResult.Fetched)?.apiKey
    }
}
