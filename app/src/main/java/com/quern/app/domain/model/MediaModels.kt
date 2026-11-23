package com.quern.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class MediaType(val stremioValue: String) {
    MOVIE("movie"),
    SERIES("series"),
    CHANNEL("channel"),
    OTHER("other");

    companion object {
        fun fromStremio(raw: String?): MediaType = when (raw?.lowercase()) {
            "movie" -> MOVIE
            "series", "tv" -> SERIES
            "channel" -> CHANNEL
            else -> OTHER
        }
    }
}

@Serializable
enum class SourceOrigin(val displayName: String) {
    ADDON("Addon"),
    TORBOX("TorBox"),
    REAL_DEBRID("Real-Debrid"),
    ALL_DEBRID("AllDebrid"),
    PREMIUMIZE("Premiumize"),
    JELLYFIN("Jellyfin"),
    EMBY("Emby"),
    PLEX("Plex"),
    WEBDAV("WebDAV"),
    TMDB("TMDB"),
    DIRECT("Direct")
}

@Immutable
@Serializable
data class MediaItem(
    val id: String,
    val title: String,
    val type: MediaType,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String? = null,
    val year: String? = null,
    val releaseDate: String? = null, // ISO YYYY-MM-DD when available from TMDB/addons
    val rating: String? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val origin: SourceOrigin = SourceOrigin.ADDON,
    val sourceId: String? = null, // Addon base URL or MediaServer ID
    val genres: List<String> = emptyList()
)

@Immutable
@Serializable
data class Episode(
    val id: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String? = null,
    val thumbnailUrl: String? = null,
    val airDate: String? = null,
    val runtimeMinutes: Int? = null
)

@Immutable
@Serializable
data class MediaDetail(
    val item: MediaItem,
    val runtime: String? = null,
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val trailerUrl: String? = null,
    val episodes: List<Episode> = emptyList()
) {
    val seasons: List<Int>
        get() = episodes.map { it.seasonNumber }.distinct().sorted()

    fun episodesForSeason(season: Int): List<Episode> =
        episodes.filter { it.seasonNumber == season }.sortedBy { it.episodeNumber }
}

@Immutable
@Serializable
data class StreamSource(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val magnetUri: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val providerName: String,
    val origin: SourceOrigin,
    val qualityBadges: List<String> = emptyList(),
    val isCached: Boolean = false,
    val sizeBytes: Long? = null,
    val subtitles: List<SubtitleTrack> = emptyList()
) {
    val isDirectlyPlayable: Boolean
        get() = !url.isNullOrBlank()

    val needsDebridResolution: Boolean
        get() = url.isNullOrBlank() && (!infoHash.isNullOrBlank() || !magnetUri.isNullOrBlank())
}

@Immutable
@Serializable
data class SubtitleTrack(
    val id: String,
    val url: String,
    val language: String,
    val label: String = language,
    val mimeType: String = guessMimeType(url)
) {
    companion object {
        fun guessMimeType(url: String): String {
            val lower = url.lowercase()
            return when {
                lower.contains(".vtt") -> "text/vtt"
                lower.contains(".ass") || lower.contains(".ssa") -> "text/x-ssa"
                lower.contains(".ttml") -> "application/ttml+xml"
                else -> "application/x-subrip"
            }
        }
    }
}

@Immutable
data class CatalogRow(
    val id: String,
    val title: String,
    val origin: SourceOrigin,
    val items: List<MediaItem>
)

@Immutable
data class WatchProgress(
    val contentId: String,
    val title: String,
    val type: MediaType,
    val posterUrl: String?,
    val backdropUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val episodeId: String? = null,
    val episodeLabel: String? = null,
    val lastStreamUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
