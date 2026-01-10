package com.diokko.player.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Playlist types supported by Diokko Player
 */
enum class PlaylistType {
    M3U,
    XTREAM_CODES
}

/**
 * Content types in playlists
 */
enum class ContentType {
    LIVE_TV,
    MOVIE,
    SERIES,
    UNKNOWN
}

/**
 * Playlist entity - stores user's IPTV playlists
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    // For M3U playlists
    val url: String? = null,
    // For Xtream Codes
    val serverUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    // EPG URL (auto-generated for Xtream Codes, can be custom for M3U)
    val epgUrl: String? = null,
    val epgLastUpdated: Long? = null,
    // Metadata
    val lastUpdated: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val channelCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0
)

/**
 * Category/Group for organizing content
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val name: String,
    val type: ContentType,
    val categoryId: String? = null, // External ID from provider
    val iconUrl: String? = null,
    val order: Int = 0
)

/**
 * Channel entity for live TV streams
 */
@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("playlistId"), Index("categoryId")]
)
data class Channel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val categoryId: Long? = null,
    val streamId: String? = null, // External stream ID
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val epgChannelId: String? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val lastWatched: Long? = null,
    val order: Int = 0,
    val isDivider: Boolean = false // Section divider like "##### FAVORITES #####"
)

/**
 * Movie entity for VOD content
 */
@Entity(
    tableName = "movies",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("playlistId"), Index("categoryId")]
)
data class Movie(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val categoryId: Long? = null,
    val streamId: String? = null,
    val name: String,
    val streamUrl: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val duration: String? = null,
    val rating: Float? = null,
    val year: Int? = null,
    val containerExtension: String? = null,
    val isFavorite: Boolean = false,
    val isWatched: Boolean = false,
    val watchProgress: Long = 0,
    val lastWatched: Long? = null
)

/**
 * Series entity for TV shows
 */
@Entity(
    tableName = "series",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("playlistId"), Index("categoryId")]
)
data class Series(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val categoryId: Long? = null,
    val seriesId: String? = null,
    val name: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: Float? = null,
    val year: Int? = null,
    val isFavorite: Boolean = false,
    val lastWatched: Long? = null
)

/**
 * Episode entity for series episodes
 */
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("seriesId")]
)
data class Episode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val seriesId: Long,
    val episodeId: String? = null,
    val name: String,
    val streamUrl: String,
    val season: Int,
    val episodeNum: Int,
    val plot: String? = null,
    val duration: String? = null,
    val posterUrl: String? = null,
    val containerExtension: String? = null,
    val isWatched: Boolean = false,
    val watchProgress: Long = 0,
    val lastWatched: Long? = null
)

/**
 * EPG Program data
 */
@Entity(
    tableName = "epg_programs",
    foreignKeys = [
        ForeignKey(
            entity = Channel::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("channelId"), Index("startTime"), Index("endTime"), Index("epgChannelId")]
)
data class EpgProgram(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: Long,
    val epgChannelId: String? = null,  // For direct matching without join
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val iconUrl: String? = null
)

/**
 * User preferences for playback
 */
@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contentType: ContentType,
    val contentId: Long,
    val contentName: String,
    val position: Long,
    val duration: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Xtream API response models
 */
@Serializable
data class XtreamUserInfo(
    val username: String? = null,
    val password: String? = null,
    val message: String? = null,
    val auth: Int? = null,
    val status: String? = null,
    val exp_date: String? = null,
    val is_trial: String? = null,
    val active_cons: String? = null,
    val created_at: String? = null,
    val max_connections: String? = null,
    val allowed_output_formats: List<String>? = null
)

@Serializable
data class XtreamServerInfo(
    val url: String? = null,
    val port: String? = null,
    val https_port: String? = null,
    val server_protocol: String? = null,
    val rtmp_port: String? = null,
    val timezone: String? = null,
    val timestamp_now: Long? = null,
    val time_now: String? = null
)

@Serializable
data class XtreamAuthResponse(
    val user_info: XtreamUserInfo? = null,
    val server_info: XtreamServerInfo? = null
)

@Serializable
data class XtreamCategory(
    val category_id: String? = null,
    val category_name: String? = null,
    val parent_id: Int? = null
)

@Serializable
data class XtreamChannel(
    val num: Int? = null,
    val name: String? = null,
    val stream_type: String? = null,
    val stream_id: Int? = null,
    val stream_icon: String? = null,
    val epg_channel_id: String? = null,
    val added: String? = null,
    val category_id: String? = null,
    val custom_sid: String? = null,
    val tv_archive: Int? = null,
    val direct_source: String? = null,
    val tv_archive_duration: Int? = null
)

@Serializable
data class XtreamMovie(
    val num: Int? = null,
    val name: String? = null,
    val stream_type: String? = null,
    val stream_id: Int? = null,
    val stream_icon: String? = null,
    val rating: String? = null,
    val rating_5based: Float? = null,
    val added: String? = null,
    val category_id: String? = null,
    val container_extension: String? = null,
    val custom_sid: String? = null,
    val direct_source: String? = null
)

@Serializable
data class XtreamSeries(
    val num: Int? = null,
    val name: String? = null,
    val series_id: Int? = null,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val last_modified: String? = null,
    val rating: String? = null,
    val rating_5based: Float? = null,
    val backdrop_path: List<String>? = null,
    val youtube_trailer: String? = null,
    val episode_run_time: String? = null,
    val category_id: String? = null
)

@Serializable
data class XtreamSeriesInfo(
    val seasons: List<XtreamSeason>? = null,
    val info: XtreamSeriesDetails? = null,
    val episodes: Map<String, List<XtreamEpisode>>? = null
)

@Serializable
data class XtreamSeason(
    val air_date: String? = null,
    val episode_count: Int? = null,
    val id: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    val season_number: Int? = null,
    val cover: String? = null,
    val cover_big: String? = null
)

@Serializable
data class XtreamSeriesDetails(
    val name: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val last_modified: String? = null,
    val rating: String? = null,
    val rating_5based: Float? = null,
    val backdrop_path: List<String>? = null,
    val youtube_trailer: String? = null,
    val episode_run_time: String? = null,
    val category_id: String? = null
)

@Serializable
data class XtreamEpisode(
    val id: String? = null,
    val episode_num: Int? = null,
    val title: String? = null,
    val container_extension: String? = null,
    val info: XtreamEpisodeInfo? = null,
    val custom_sid: String? = null,
    val added: String? = null,
    val season: Int? = null,
    val direct_source: String? = null
)

@Serializable
data class XtreamEpisodeInfo(
    val movie_image: String? = null,
    val plot: String? = null,
    val releasedate: String? = null,
    val rating: Float? = null,
    val duration_secs: Int? = null,
    val duration: String? = null,
    val bitrate: Int? = null
)

/**
 * Sealed class for type-safe search results.
 * Replaces Triple<String, Any, Unit> for better maintainability.
 */
sealed class SearchResult {
    abstract val id: Long
    abstract val name: String
    abstract val subtitle: String?
    abstract val emoji: String
    
    data class ChannelResult(val channel: Channel) : SearchResult() {
        override val id: Long get() = channel.id
        override val name: String get() = channel.name
        override val subtitle: String? get() = channel.groupTitle
        override val emoji: String get() = "📺"
    }
    
    data class MovieResult(val movie: Movie) : SearchResult() {
        override val id: Long get() = movie.id
        override val name: String get() = movie.name
        override val subtitle: String? get() = movie.genre
        override val emoji: String get() = "🎬"
    }
    
    data class SeriesResult(val series: Series) : SearchResult() {
        override val id: Long get() = series.id
        override val name: String get() = series.name
        override val subtitle: String? get() = series.genre
        override val emoji: String get() = "📺"
    }
}
