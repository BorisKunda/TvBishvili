package com.bk.mmovies.ui.screen.details

import android.content.Context
import com.bk.mmovies.ui.screen.news.shareText

private const val TMDB_MOVIE_PAGE_URL = "https://www.themoviedb.org/movie/"
private const val TMDB_TV_SERIES_PAGE_URL = "https://www.themoviedb.org/tv/"
private val nonAlphanumericRuns = Regex("[^a-z0-9]+")

fun shareMovieTmdbPage(context: Context, movieId: Int, title: String?) {
    shareText(context, "$TMDB_MOVIE_PAGE_URL${tmdbIdWithSlug(movieId, title)}")
}

fun shareTvSeriesTmdbPage(context: Context, seriesId: Int, title: String?) {
    shareText(context, "$TMDB_TV_SERIES_PAGE_URL${tmdbIdWithSlug(seriesId, title)}")
}

// TMDB pages look like /movie/603-the-matrix, but the slug is cosmetic (the
// bare id redirects), so a title with no latin letters or digits just yields
// the id.
private fun tmdbIdWithSlug(id: Int, title: String?): String {
    val slug = title.orEmpty().lowercase().replace(nonAlphanumericRuns, "-").trim('-')
    return if (slug.isEmpty()) "$id" else "$id-$slug"
}
