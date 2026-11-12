package com.bk.mmovies.data.source.remote.dto

import com.bk.mmovies.data.source.remote.TMDB_ERROR_CODE_INVALID_API_KEY
import com.bk.mmovies.data.source.remote.TMDB_ERROR_CODE_SUSPENDED_API_KEY
import com.google.gson.annotations.SerializedName

data class ErrorDto(
        @SerializedName("status_message") val message: String?,
        @SerializedName("status_code") val statusCode: Int?
                   )

val ErrorDto?.indicatesRejectedApiKey: Boolean
    get() = this?.statusCode == TMDB_ERROR_CODE_INVALID_API_KEY ||
            this?.statusCode == TMDB_ERROR_CODE_SUSPENDED_API_KEY
