package com.diokko.player.data.parser

import com.diokko.player.data.models.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xtream Codes API Client for fetching IPTV content
 */
@Singleton
class XtreamCodesApi @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json
) {
    
    /**
     * Build base URL from server URL, username, and password
     */
    private fun buildBaseUrl(serverUrl: String, username: String, password: String): String {
        val baseUrl = serverUrl.removeSuffix("/")
        return "$baseUrl/player_api.php?username=$username&password=$password"
    }

    /**
     * Build stream URL for live TV
     */
    fun buildLiveStreamUrl(serverUrl: String, username: String, password: String, streamId: Int): String {
        val baseUrl = serverUrl.removeSuffix("/")
        return "$baseUrl/live/$username/$password/$streamId.ts"
    }

    /**
     * Build stream URL for VOD (movies)
     */
    fun buildVodStreamUrl(serverUrl: String, username: String, password: String, streamId: Int, extension: String = "mp4"): String {
        val baseUrl = serverUrl.removeSuffix("/")
        return "$baseUrl/movie/$username/$password/$streamId.$extension"
    }

    /**
     * Build stream URL for series episodes
     */
    fun buildSeriesStreamUrl(serverUrl: String, username: String, password: String, episodeId: String, extension: String = "mp4"): String {
        val baseUrl = serverUrl.removeSuffix("/")
        return "$baseUrl/series/$username/$password/$episodeId.$extension"
    }

    /**
     * Authenticate and get server info
     */
    suspend fun authenticate(serverUrl: String, username: String, password: String): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        try {
            val url = buildBaseUrl(serverUrl, username, password)
            val request = Request.Builder().url(url).get().build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Authentication failed: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val authResponse = json.decodeFromString<XtreamAuthResponse>(body)
            
            if (authResponse.user_info?.auth != 1) {
                return@withContext Result.failure(Exception("Invalid credentials"))
            }
            
            Result.success(authResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get live TV categories
     */
    suspend fun getLiveCategories(serverUrl: String, username: String, password: String): Result<List<XtreamCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = "${buildBaseUrl(serverUrl, username, password)}&action=get_live_categories"
            val request = Request.Builder().url(url).get().build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get categories: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val categories = json.decodeFromString<List<XtreamCategory>>(body)
            
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get VOD (movie) categories
     */
    suspend fun getVodCategories(serverUrl: String, username: String, password: String): Result<List<XtreamCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = "${buildBaseUrl(serverUrl, username, password)}&action=get_vod_categories"
            val request = Request.Builder().url(url).get().build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get VOD categories: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val categories = json.decodeFromString<List<XtreamCategory>>(body)
            
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get series categories
     */
    suspend fun getSeriesCategories(serverUrl: String, username: String, password: String): Result<List<XtreamCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = "${buildBaseUrl(serverUrl, username, password)}&action=get_series_categories"
            val request = Request.Builder().url(url).get().build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get series categories: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val categories = json.decodeFromString<List<XtreamCategory>>(body)
            
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get live TV streams
     */
    suspend fun getLiveStreams(serverUrl: String, username: String, password: String, categoryId: String? = null): Result<List<XtreamChannel>> = withContext(Dispatchers.IO) {
        try {
            var url = "${buildBaseUrl(serverUrl, username, password)}&action=get_live_streams"
            categoryId?.let { url += "&category_id=$it" }
            
            val request = Request.Builder().url(url).get().build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get live streams: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val channels = json.decodeFromString<List<XtreamChannel>>(body)
            
            Result.success(channels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get VOD (movie) streams
     */
    suspend fun getVodStreams(serverUrl: String, username: String, password: String, categoryId: String? = null): Result<List<XtreamMovie>> = withContext(Dispatchers.IO) {
        try {
            var url = "${buildBaseUrl(serverUrl, username, password)}&action=get_vod_streams"
            categoryId?.let { url += "&category_id=$it" }
            
            val request = Request.Builder().url(url).get().build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get VOD streams: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val movies = json.decodeFromString<List<XtreamMovie>>(body)
            
            Result.success(movies)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get series list
     */
    suspend fun getSeries(serverUrl: String, username: String, password: String, categoryId: String? = null): Result<List<XtreamSeries>> = withContext(Dispatchers.IO) {
        try {
            var url = "${buildBaseUrl(serverUrl, username, password)}&action=get_series"
            categoryId?.let { url += "&category_id=$it" }
            
            val request = Request.Builder().url(url).get().build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get series: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val series = json.decodeFromString<List<XtreamSeries>>(body)
            
            Result.success(series)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get series info with episodes
     */
    suspend fun getSeriesInfo(serverUrl: String, username: String, password: String, seriesId: Int): Result<XtreamSeriesInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "${buildBaseUrl(serverUrl, username, password)}&action=get_series_info&series_id=$seriesId"
            val request = Request.Builder().url(url).get().build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get series info: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val seriesInfo = json.decodeFromString<XtreamSeriesInfo>(body)
            
            Result.success(seriesInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Data class for parsed Xtream content
 */
data class XtreamContent(
    val channels: List<Channel>,
    val movies: List<Movie>,
    val series: List<Series>,
    val liveCategories: List<Category>,
    val vodCategories: List<Category>,
    val seriesCategories: List<Category>
)

/**
 * Helper class for converting Xtream API responses to local models
 */
@Singleton
class XtreamContentConverter @Inject constructor(
    private val xtreamApi: XtreamCodesApi
) {
    
    /**
     * Fetch and convert all content from Xtream Codes provider
     */
    suspend fun fetchAllContent(
        playlist: Playlist
    ): Result<XtreamContent> {
        val serverUrl = playlist.serverUrl ?: return Result.failure(Exception("Server URL is required"))
        val username = playlist.username ?: return Result.failure(Exception("Username is required"))
        val password = playlist.password ?: return Result.failure(Exception("Password is required"))
        
        // Authenticate first
        val authResult = xtreamApi.authenticate(serverUrl, username, password)
        if (authResult.isFailure) {
            return Result.failure(authResult.exceptionOrNull() ?: Exception("Authentication failed"))
        }
        
        val channels = mutableListOf<Channel>()
        val movies = mutableListOf<Movie>()
        val series = mutableListOf<Series>()
        val liveCategories = mutableListOf<Category>()
        val vodCategories = mutableListOf<Category>()
        val seriesCategories = mutableListOf<Category>()
        
        // Fetch live categories and channels
        xtreamApi.getLiveCategories(serverUrl, username, password).getOrNull()?.let { categories ->
            categories.forEach { cat ->
                liveCategories.add(
                    Category(
                        playlistId = playlist.id,
                        name = cat.category_name ?: "Unknown",
                        type = ContentType.LIVE_TV,
                        categoryId = cat.category_id
                    )
                )
            }
        }
        
        xtreamApi.getLiveStreams(serverUrl, username, password).getOrNull()?.let { streams ->
            streams.forEachIndexed { index, stream ->
                channels.add(
                    Channel(
                        playlistId = playlist.id,
                        streamId = stream.stream_id?.toString(),
                        name = stream.name ?: "Unknown Channel",
                        streamUrl = xtreamApi.buildLiveStreamUrl(serverUrl, username, password, stream.stream_id ?: 0),
                        logoUrl = stream.stream_icon,
                        groupTitle = stream.category_id,
                        epgChannelId = stream.epg_channel_id,
                        order = stream.num ?: index
                    )
                )
            }
        }
        
        // Fetch VOD categories and movies
        xtreamApi.getVodCategories(serverUrl, username, password).getOrNull()?.let { categories ->
            categories.forEach { cat ->
                vodCategories.add(
                    Category(
                        playlistId = playlist.id,
                        name = cat.category_name ?: "Unknown",
                        type = ContentType.MOVIE,
                        categoryId = cat.category_id
                    )
                )
            }
        }
        
        xtreamApi.getVodStreams(serverUrl, username, password).getOrNull()?.let { streams ->
            streams.forEach { stream ->
                movies.add(
                    Movie(
                        playlistId = playlist.id,
                        streamId = stream.stream_id?.toString(),
                        name = stream.name ?: "Unknown Movie",
                        streamUrl = xtreamApi.buildVodStreamUrl(
                            serverUrl, username, password,
                            stream.stream_id ?: 0,
                            stream.container_extension ?: "mp4"
                        ),
                        posterUrl = stream.stream_icon,
                        rating = stream.rating_5based,
                        containerExtension = stream.container_extension
                    )
                )
            }
        }
        
        // Fetch series categories and series
        xtreamApi.getSeriesCategories(serverUrl, username, password).getOrNull()?.let { categories ->
            categories.forEach { cat ->
                seriesCategories.add(
                    Category(
                        playlistId = playlist.id,
                        name = cat.category_name ?: "Unknown",
                        type = ContentType.SERIES,
                        categoryId = cat.category_id
                    )
                )
            }
        }
        
        xtreamApi.getSeries(serverUrl, username, password).getOrNull()?.let { seriesList ->
            seriesList.forEach { s ->
                series.add(
                    Series(
                        playlistId = playlist.id,
                        seriesId = s.series_id?.toString(),
                        name = s.name ?: "Unknown Series",
                        posterUrl = s.cover,
                        plot = s.plot,
                        cast = s.cast,
                        director = s.director,
                        genre = s.genre,
                        releaseDate = s.releaseDate,
                        rating = s.rating_5based,
                        backdropUrl = s.backdrop_path?.firstOrNull()
                    )
                )
            }
        }
        
        return Result.success(
            XtreamContent(
                channels = channels,
                movies = movies,
                series = series,
                liveCategories = liveCategories,
                vodCategories = vodCategories,
                seriesCategories = seriesCategories
            )
        )
    }
}
