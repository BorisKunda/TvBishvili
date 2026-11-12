package com.bk.mmovies.data.source.remote.keyrefresh

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

fun interface ApiKeyRefresher {
    suspend fun fetchReplacementApiKey(): String?
}

@Singleton
class ApiKeyRefresherHolder @Inject constructor() {
    @Volatile
    var refresher: ApiKeyRefresher? = null
}

@Singleton
class ApiKeyRejectionEvents @Inject constructor() {
    private val mutableRejections = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
                                                           )

    val rejections: SharedFlow<Unit> = mutableRejections.asSharedFlow()

    fun notifyRejected() {
        mutableRejections.tryEmit(Unit)
    }
}
