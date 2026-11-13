package com.remotemedia.sources

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * M5.4 — TMDB metadata resolver.
 * Looks up poster, backdrop, and overview for movie/show titles.
 * Safely falls back to local title/year parsing when no API key is configured.
 */
data class TmdbMetadata(
    val title: String,
    val year: Int? = null,
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val voteAverage: Double? = null
)

object TmdbClient {

    private val gson = Gson()
    @Volatile private var apiKey: String? = null

    fun configure(key: String?) {
        apiKey = key?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Resolves metadata for [rawTitle].
     * If an API key is configured, queries the TMDB Search API.
     * Otherwise, parses title/year locally and returns clean fallback metadata.
     */
    suspend fun resolve(
        rawTitle: String,
        yearHint: Int? = null,
        timeoutMs: Int = 6_000
    ): TmdbMetadata = withContext(Dispatchers.IO) {
        val (cleanTitle, parsedYear) = parseTitleAndYear(rawTitle)
        val year = yearHint ?: parsedYear

        val key = apiKey
        if (key.isNullOrBlank()) {
            return@withContext TmdbMetadata(
                title = cleanTitle,
                year = year,
                overview = "No TMDB API key configured."
            )
        }

        try {
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            val yearParam = if (year != null) "&year=$year" else ""
            val urlStr = "https://api.themoviedb.org/3/search/movie?api_key=$key&query=$encodedTitle$yearParam"

            val jsonStr = httpGet(urlStr, timeoutMs) ?: return@withContext TmdbMetadata(cleanTitle, year)
            val root = gson.fromJson(jsonStr, JsonObject::class.java)
            val results = root.getAsJsonArray("results")

            if (results != null && results.size() > 0) {
                val first = results.get(0).asJsonObject
                val title = first["title"]?.asString ?: cleanTitle
                val overview = first["overview"]?.asString ?: ""
                val posterPath = first["poster_path"]?.asString
                val backdropPath = first["backdrop_path"]?.asString
                val voteAvg = first["vote_average"]?.asDouble
                val releaseDate = first["release_date"]?.asString
                val releaseYear = releaseDate?.take(4)?.toIntOrNull() ?: year

                TmdbMetadata(
                    title = title,
                    year = releaseYear,
                    overview = overview,
                    posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" },
                    voteAverage = voteAvg
                )
            } else {
                TmdbMetadata(cleanTitle, year)
            }
        } catch (_: Exception) {
            TmdbMetadata(cleanTitle, year)
        }
    }

    /** Extracts clean title and 4-digit release year from typical media filenames. */
    fun parseTitleAndYear(raw: String): Pair<String, Int?> {
        val yearMatcher = Pattern.compile("""[.\s_(\[]((?:19|20)\d{2})[.\s_)\]]""").matcher(raw)
        var year: Int? = null
        var titlePart = raw

        if (yearMatcher.find()) {
            year = yearMatcher.group(1)?.toIntOrNull()
            titlePart = raw.substring(0, yearMatcher.start())
        }

        // Clean dots, underscores, dashes
        val clean = titlePart.replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return Pair(clean.ifBlank { raw }, year)
    }

    private fun httpGet(urlStr: String, timeoutMs: Int): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "PocketNode/1.0")
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
