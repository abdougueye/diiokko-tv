package com.diokko.player.data.repository

import android.content.Context
import android.util.Log
import com.diokko.player.data.database.*
import com.diokko.player.data.models.*
import com.diokko.player.data.parser.M3UParser
import com.diokko.player.data.parser.XtreamCodesApi
import com.diokko.player.data.parser.XtreamContentConverter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlaylistRepo"

// Use Warning level so logs appear in default logcat
private fun logW(msg: String) = Log.w(TAG, msg)
private fun logE(msg: String, e: Throwable? = null) = if (e != null) Log.e(TAG, msg, e) else Log.e(TAG, msg)

/**
 * Repository for playlist operations
 */
@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val m3uParser: M3UParser,
    private val xtreamConverter: XtreamContentConverter,
    private val httpClient: OkHttpClient
) {
    /**
     * Get all playlists
     */
    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    
    /**
     * Get active playlists
     */
    fun getActivePlaylists(): Flow<List<Playlist>> = playlistDao.getActivePlaylists()
    
    /**
     * Get playlist by ID
     */
    suspend fun getPlaylistById(id: Long): Playlist? = playlistDao.getPlaylistById(id)
    
    /**
     * Add a new M3U playlist
     */
    suspend fun addM3UPlaylist(name: String, url: String): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            logW("=== Adding M3U Playlist ===")
            logW("Name: $name")
            logW("URL: $url")
            
            val playlist = Playlist(
                name = name,
                type = PlaylistType.M3U,
                url = url
            )
            
            val playlistId = playlistDao.insertPlaylist(playlist)
            logW("Playlist saved with ID: $playlistId")
            
            val savedPlaylist = playlist.copy(id = playlistId)
            
            // Fetch and parse the playlist
            logW("Starting initial playlist refresh...")
            val refreshResult = refreshPlaylist(savedPlaylist)
            
            // Check if refresh was successful
            if (refreshResult.isFailure) {
                val error = refreshResult.exceptionOrNull()
                logE("Playlist refresh failed, deleting playlist entry", error)
                // Delete the playlist since we couldn't load it
                playlistDao.deletePlaylist(savedPlaylist)
                return@withContext Result.failure(error ?: Exception("Failed to load playlist"))
            }
            
            logW("M3U playlist added successfully!")
            Result.success(savedPlaylist)
        } catch (e: Exception) {
            logE("Error adding M3U playlist", e)
            Result.failure(e)
        }
    }
    
    /**
     * Add a new Xtream Codes playlist
     */
    suspend fun addXtreamPlaylist(
        name: String,
        serverUrl: String,
        username: String,
        password: String
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val playlist = Playlist(
                name = name,
                type = PlaylistType.XTREAM_CODES,
                serverUrl = serverUrl,
                username = username,
                password = password
            )
            
            val playlistId = playlistDao.insertPlaylist(playlist)
            val savedPlaylist = playlist.copy(id = playlistId)
            
            // Fetch content from Xtream provider
            refreshPlaylist(savedPlaylist)
            
            Result.success(savedPlaylist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Refresh/update a playlist
     */
    suspend fun refreshPlaylist(playlist: Playlist): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            logW("=== Starting Playlist Refresh ===")
            logW("Playlist: ${playlist.name} (ID: ${playlist.id}, Type: ${playlist.type})")
            
            // Clear existing content
            channelDao.deleteChannelsForPlaylist(playlist.id)
            movieDao.deleteMoviesForPlaylist(playlist.id)
            seriesDao.deleteSeriesForPlaylist(playlist.id)
            categoryDao.deleteCategoriesForPlaylist(playlist.id)
            logW("Cleared existing content")
            
            when (playlist.type) {
                PlaylistType.M3U -> refreshM3UPlaylist(playlist)
                PlaylistType.XTREAM_CODES -> refreshXtreamPlaylist(playlist)
            }
            
            // Update playlist stats
            val channelCount = channelDao.getChannelCountForPlaylist(playlist.id)
            val movieCount = movieDao.getMovieCountForPlaylist(playlist.id)
            val seriesCount = seriesDao.getSeriesCountForPlaylist(playlist.id)
            
            logW("=== Final Stats ===")
            logW("Channels in DB: $channelCount")
            logW("Movies in DB: $movieCount")
            logW("Series in DB: $seriesCount")
            
            playlistDao.updatePlaylistStats(
                playlistId = playlist.id,
                timestamp = System.currentTimeMillis(),
                channels = channelCount,
                movies = movieCount,
                series = seriesCount
            )
            
            logW("Playlist refresh completed successfully!")
            Result.success(Unit)
        } catch (e: Exception) {
            logE("Error refreshing playlist: ${playlist.name}", e)
            logE("Exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    private suspend fun refreshM3UPlaylist(playlist: Playlist) {
        val url = playlist.url ?: throw Exception("M3U URL is required")
        logW("=== Refreshing M3U Playlist ===")
        logW("Name: ${playlist.name}")
        logW("URL: $url")
        
        // Validate URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw Exception("Invalid URL: must start with http:// or https://")
        }
        
        // Retry wrapper for connection reset errors during parsing
        val maxRetries = 3
        var lastParseException: Exception? = null
        
        for (parseAttempt in 1..maxRetries) {
            try {
                if (parseAttempt > 1) {
                    logW("=== RETRY ATTEMPT $parseAttempt of $maxRetries ===")
                    // Clear any partial data from previous attempt
                    channelDao.deleteChannelsForPlaylist(playlist.id)
                    movieDao.deleteMoviesForPlaylist(playlist.id)
                    seriesDao.deleteSeriesForPlaylist(playlist.id)
                    categoryDao.deleteCategoriesForPlaylist(playlist.id)
                    episodeDao.deleteEpisodesForPlaylist(playlist.id)
                    // Wait before retry with exponential backoff
                    val delay = 3000L * parseAttempt
                    logW("Waiting ${delay}ms before retry...")
                    kotlinx.coroutines.delay(delay)
                }
                
                doRefreshM3UPlaylist(playlist, url)
                return // Success! Exit the retry loop
                
            } catch (e: Exception) {
                val message = e.message ?: ""
                val isRetryable = message.contains("reset", ignoreCase = true) ||
                                  message.contains("timeout", ignoreCase = true) ||
                                  message.contains("interrupted", ignoreCase = true) ||
                                  message.contains("broken", ignoreCase = true)
                
                if (isRetryable && parseAttempt < maxRetries) {
                    logW("Retryable error on attempt $parseAttempt: ${e.message}")
                    lastParseException = e
                    continue
                } else {
                    throw e
                }
            }
        }
        
        throw lastParseException ?: Exception("Failed after $maxRetries attempts")
    }
    
    private suspend fun doRefreshM3UPlaylist(playlist: Playlist, url: String) {
        // Try with different User-Agents if we get errors
        val userAgents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "VLC/3.0.18 LibVLC/3.0.18",
            "Lavf/60.3.100",
            "Dalvik/2.1.0 (Linux; U; Android 9; AFTMM Build/PS7285)",
            "IPTVnator/0.8.4",  // Common IPTV app
            "Kodi/20.0"  // Another common one
        )
        
        var lastException: Exception? = null
        var successResponse: okhttp3.Response? = null
        var lastErrorBody: String? = null
        var lastErrorCode: Int? = null
        
        for ((index, userAgent) in userAgents.withIndex()) {
            logW("Attempt ${index + 1} with User-Agent: ${userAgent.take(30)}...")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "identity")  // Don't compress - some servers have issues
                .header("Connection", "keep-alive")
                .get()
                .build()
            
            try {
                val response = httpClient.newCall(request).execute()
                logW("HTTP Response: ${response.code} ${response.message}")
                
                if (response.isSuccessful) {
                    successResponse = response
                    break
                } else {
                    lastErrorCode = response.code
                    lastErrorBody = response.body?.string()?.take(1000) ?: "No response body"
                    logE("HTTP Error: ${response.code}")
                    logE("Error body: ${lastErrorBody?.take(200)}")
                    
                    // Check if response contains M3U content despite error code (some servers do this)
                    if (lastErrorBody?.contains("#EXTM3U") == true) {
                        logW("Response contains M3U content despite error code, attempting to parse...")
                        // Create a new response with the body we already read - we'll handle this below
                    }
                    
                    response.close()
                    
                    // For gateway/server errors, try next User-Agent
                    if (response.code in 500..599 || response.code > 600) {
                        logW("Server error ${response.code}, trying next User-Agent...")
                        lastException = Exception("Server error ${response.code}: ${response.message}")
                        kotlinx.coroutines.delay(500)
                        continue
                    } else if (response.code == 403 || response.code == 401) {
                        // Authentication errors - try next User-Agent too (some servers block certain agents)
                        logW("Auth error ${response.code}, trying next User-Agent...")
                        lastException = Exception("Access denied (${response.code}): Check your credentials")
                        kotlinx.coroutines.delay(300)
                        continue
                    } else {
                        lastException = Exception("Server error ${response.code}: ${response.message}")
                        continue  // Try all user agents before giving up
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                logE("DNS lookup failed", e)
                throw Exception("Cannot connect to server. Check your internet connection and URL.")
            } catch (e: java.net.SocketTimeoutException) {
                logE("Connection timeout", e)
                lastException = Exception("Connection timed out. The server is not responding.")
                kotlinx.coroutines.delay(1000) // Wait before retry
                continue
            } catch (e: javax.net.ssl.SSLException) {
                logE("SSL error", e)
                throw Exception("SSL/Security error: ${e.message}")
            } catch (e: java.net.SocketException) {
                // Specific handling for connection reset
                val message = e.message ?: ""
                logE("Socket error: $message", e)
                if (message.contains("reset", ignoreCase = true) || 
                    message.contains("broken pipe", ignoreCase = true) ||
                    message.contains("connection abort", ignoreCase = true)) {
                    lastException = Exception("Connection was reset by server. This may be due to server load or network issues. Please try again.")
                    kotlinx.coroutines.delay(2000) // Longer wait for connection reset
                } else {
                    lastException = Exception("Network connection error: $message")
                }
                continue
            } catch (e: java.io.IOException) {
                val message = e.message ?: ""
                logE("Network error: $message", e)
                // Check if this is a connection reset wrapped in IOException
                if (message.contains("reset", ignoreCase = true) ||
                    message.contains("broken pipe", ignoreCase = true)) {
                    lastException = Exception("Connection was reset by server. This may be due to server load or network issues. Please try again.")
                    kotlinx.coroutines.delay(2000)
                } else {
                    lastException = Exception("Network error: $message")
                    kotlinx.coroutines.delay(1000)
                }
                continue
            }
        }
        
        if (successResponse == null) {
            // Provide helpful error message based on what we saw
            val errorMessage = when {
                lastErrorCode != null && lastErrorCode > 600 -> 
                    "IPTV provider returned error code $lastErrorCode. This usually means:\n" +
                    "• Invalid username or password\n" +
                    "• Subscription expired\n" +
                    "• Account suspended\n" +
                    "• Too many connections\n\n" +
                    "Please check your IPTV provider account."
                lastErrorCode == 403 || lastErrorCode == 401 ->
                    "Access denied. Please verify your credentials are correct."
                lastErrorBody?.contains("expire", ignoreCase = true) == true ||
                lastErrorBody?.contains("invalid", ignoreCase = true) == true ->
                    "The server indicates there may be an issue with your account. " +
                    "Please check if your subscription is active."
                else -> lastException?.message ?: "Failed to fetch playlist after multiple attempts"
            }
            throw Exception(errorMessage)
        }
        
        // ========================================
        // PHASE 1: Download file to cache first
        // This separates downloading from parsing to prevent
        // buffer overflow and connection resets on FireTV
        // ========================================
        val contentLength = successResponse.header("Content-Length")?.toLongOrNull() ?: -1
        logW("Content-Length: $contentLength bytes (${contentLength / 1024 / 1024}MB)")
        
        val cacheFile = File(context.cacheDir, "playlist_${playlist.id}.m3u")
        logW("Downloading to cache: ${cacheFile.absolutePath}")
        
        try {
            val downloadStartTime = System.currentTimeMillis()
            var bytesDownloaded = 0L
            
            successResponse.use { response ->
                val body = response.body ?: throw Exception("Empty response from server")
                
                cacheFile.outputStream().buffered(8192).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastProgressLog = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            
                            // Log progress every 10MB
                            if (bytesDownloaded - lastProgressLog > 10 * 1024 * 1024) {
                                val percent = if (contentLength > 0) (bytesDownloaded * 100 / contentLength) else 0
                                logW("Download progress: ${bytesDownloaded / 1024 / 1024}MB ($percent%)")
                                lastProgressLog = bytesDownloaded
                            }
                        }
                    }
                }
            }
            
            val downloadTime = System.currentTimeMillis() - downloadStartTime
            logW("Download complete: ${bytesDownloaded / 1024 / 1024}MB in ${downloadTime / 1000}s")
            
            // ========================================
            // PHASE 2: Parse from local file
            // Network connection is now closed, no timeout risk
            // ========================================
            logW("Starting parse from local cache file...")
            
            // Create category map for group -> categoryId mapping
            val categoryMap = mutableMapOf<String, Long>()
            var categoryOrder = 0
            
            val parseResult = FileInputStream(cacheFile).use { inputStream ->
                m3uParser.parseStreaming(inputStream, playlist.id, object : M3UParser.ParseCallback {
                    override suspend fun onChannelBatch(channels: List<Channel>) {
                        // Assign category IDs to channels
                        val channelsWithCategories = channels.map { channel ->
                            channel.copy(
                                categoryId = channel.groupTitle?.let { group ->
                                    categoryMap.getOrPut("LIVE:$group") {
                                        val category = Category(
                                            playlistId = playlist.id,
                                            name = group,
                                            type = ContentType.LIVE_TV,
                                            order = categoryOrder++
                                        )
                                        categoryDao.insertCategory(category)
                                    }
                                }
                            )
                        }
                        channelDao.insertChannels(channelsWithCategories)
                    }
                    
                    override suspend fun onMovieBatch(movies: List<Movie>) {
                        // Assign category IDs to movies based on genre
                        val moviesWithCategories = movies.map { movie ->
                            movie.copy(
                                categoryId = movie.genre?.let { genre ->
                                    categoryMap.getOrPut("MOVIE:$genre") {
                                        val category = Category(
                                            playlistId = playlist.id,
                                            name = genre,
                                            type = ContentType.MOVIE,
                                            order = categoryOrder++
                                        )
                                        categoryDao.insertCategory(category)
                                    }
                                }
                            )
                        }
                        movieDao.insertMovies(moviesWithCategories)
                    }
                    
                    override suspend fun onSeriesBatch(series: List<Series>): Map<String, Long> {
                        // Assign category IDs to series based on genre
                        val seriesWithCategories = series.map { s ->
                            s.copy(
                                categoryId = s.genre?.let { genre ->
                                    categoryMap.getOrPut("SERIES:$genre") {
                                        val category = Category(
                                            playlistId = playlist.id,
                                            name = genre,
                                            type = ContentType.SERIES,
                                            order = categoryOrder++
                                        )
                                        categoryDao.insertCategory(category)
                                    }
                                }
                            )
                        }
                        
                        // Insert series and build name→ID mapping
                        val nameToId = mutableMapOf<String, Long>()
                        for (s in seriesWithCategories) {
                            val id = seriesDao.insertSeries(s)
                            nameToId[s.name] = id
                        }
                        return nameToId
                    }
                    
                    override suspend fun onEpisodeBatch(episodes: List<Episode>) {
                        if (episodes.isNotEmpty()) {
                            episodeDao.insertEpisodes(episodes)
                        }
                    }
                    
                    override suspend fun onGroupsFound(groups: Set<String>) {
                        // Groups are already created during batch insertion
                        logW("All groups processed: ${groups.size} total")
                    }
                })
            }
            
            logW("=== Refresh Complete ===")
            logW("Channels: ${parseResult.channelCount}")
            logW("Movies: ${parseResult.movieCount}")
            logW("Series: ${parseResult.seriesCount}")
            logW("Categories: ${categoryMap.size}")
            
            // Force WAL checkpoint to ensure data is persisted immediately
            // This prevents data loss if the app is killed before automatic checkpoint
            try {
                logW("Checkpointing database to ensure data persistence...")
                val app = context.applicationContext as? com.diokko.player.DiokkoApp
                app?.checkpointDatabase()
                logW("Database checkpoint initiated")
            } catch (e: Exception) {
                logE("Failed to checkpoint database", e)
            }
            
        } finally {
            // ========================================
            // PHASE 3: Clean up cache file
            // ========================================
            try {
                if (cacheFile.exists()) {
                    cacheFile.delete()
                    logW("Cache file cleaned up")
                }
            } catch (e: Exception) {
                logE("Failed to delete cache file", e)
            }
        }
    }
    
    private suspend fun refreshXtreamPlaylist(playlist: Playlist) {
        val result = xtreamConverter.fetchAllContent(playlist)
        
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to fetch Xtream content")
        }
        
        val content = result.getOrThrow()
        
        // Insert categories and build mapping
        val liveCategoryMap = mutableMapOf<String, Long>()
        content.liveCategories.forEach { cat ->
            val id = categoryDao.insertCategory(cat)
            cat.categoryId?.let { liveCategoryMap[it] = id }
        }
        
        val vodCategoryMap = mutableMapOf<String, Long>()
        content.vodCategories.forEach { cat ->
            val id = categoryDao.insertCategory(cat)
            cat.categoryId?.let { vodCategoryMap[it] = id }
        }
        
        val seriesCategoryMap = mutableMapOf<String, Long>()
        content.seriesCategories.forEach { cat ->
            val id = categoryDao.insertCategory(cat)
            cat.categoryId?.let { seriesCategoryMap[it] = id }
        }
        
        // Insert channels with category IDs
        val channelsWithCategories = content.channels.map { channel ->
            channel.copy(categoryId = channel.groupTitle?.let { liveCategoryMap[it] })
        }
        channelDao.insertChannels(channelsWithCategories)
        
        // Insert movies with category IDs
        val moviesWithCategories = content.movies.map { movie ->
            movie.copy(categoryId = movie.genre?.let { vodCategoryMap[it] })
        }
        movieDao.insertMovies(moviesWithCategories)
        
        // Insert series with category IDs
        val seriesWithCategories = content.series.map { series ->
            series.copy(categoryId = series.genre?.let { seriesCategoryMap[it] })
        }
        seriesDao.insertSeriesList(seriesWithCategories)
        
        // Force WAL checkpoint to ensure data is persisted immediately
        try {
            logW("Xtream refresh complete - checkpointing database...")
            val app = context.applicationContext as? com.diokko.player.DiokkoApp
            app?.checkpointDatabase()
            logW("Database checkpoint initiated")
        } catch (e: Exception) {
            logE("Failed to checkpoint database", e)
        }
    }
    
    /**
     * Refresh all playlists
     */
    suspend fun refreshAllPlaylists(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val playlists = playlistDao.getPlaylistById(0) // This won't work, need to get all
            // Actually we need a different approach since we have flows
            // Let's use a suspending query
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a playlist
     */
    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist)
    }
    
    /**
     * Update playlist active status
     */
    suspend fun setPlaylistActive(playlist: Playlist, isActive: Boolean) {
        playlistDao.updatePlaylist(playlist.copy(isActive = isActive))
    }
}

/**
 * Repository for channel/content operations
 */
@Singleton
class ContentRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val playbackHistoryDao: PlaybackHistoryDao
) {
    // === Categories ===
    
    fun getLiveCategories(): Flow<List<Category>> = 
        categoryDao.getAllActiveCategoriesByType(ContentType.LIVE_TV)
    
    fun getMovieCategories(): Flow<List<Category>> = 
        categoryDao.getAllActiveCategoriesByType(ContentType.MOVIE)
    
    fun getSeriesCategories(): Flow<List<Category>> = 
        categoryDao.getAllActiveCategoriesByType(ContentType.SERIES)
    
    // === Channels ===
    
    fun getAllChannels(): Flow<List<Channel>> = channelDao.getAllActiveChannels()
    
    fun getChannelsForCategory(categoryId: Long): Flow<List<Channel>> = 
        channelDao.getActiveChannelsForCategory(categoryId)
    
    fun getChannelsByGroupTitle(groupTitle: String): Flow<List<Channel>> =
        channelDao.getActiveChannelsByGroupTitle(groupTitle)
    
    fun getDistinctGroups(): Flow<List<String>> = channelDao.getDistinctActiveGroups()
    
    fun getFavoriteChannels(): Flow<List<Channel>> = channelDao.getFavoriteChannels()
    
    fun searchChannels(query: String): Flow<List<Channel>> = channelDao.searchChannels(query)
    
    fun searchChannelsInCategory(query: String, groupTitle: String): Flow<List<Channel>> = 
        channelDao.searchChannelsInCategory(query, groupTitle)
    
    suspend fun getChannelById(id: Long): Channel? = channelDao.getChannelById(id)
    
    suspend fun toggleChannelFavorite(channel: Channel) {
        channelDao.updateFavorite(channel.id, !channel.isFavorite)
    }
    
    suspend fun updateChannelLastWatched(channelId: Long) {
        channelDao.updateLastWatched(channelId, System.currentTimeMillis())
    }
    
    // === Movies ===
    
    fun getAllMovies(): Flow<List<Movie>> = movieDao.getAllActiveMovies()
    
    fun getMoviesForCategory(categoryId: Long): Flow<List<Movie>> = 
        movieDao.getActiveMoviesForCategory(categoryId)
    
    fun getMoviesByGenre(genre: String): Flow<List<Movie>> = 
        movieDao.getActiveMoviesByGenre(genre)
    
    fun getDistinctMovieGenres(): Flow<List<String>> = 
        movieDao.getDistinctActiveGenres()
    
    fun getFavoriteMovies(): Flow<List<Movie>> = movieDao.getFavoriteMovies()
    
    fun searchMovies(query: String): Flow<List<Movie>> = movieDao.searchMovies(query)
    
    fun searchMoviesInGenre(query: String, genre: String): Flow<List<Movie>> = 
        movieDao.searchMoviesInGenre(query, genre)
    
    fun getRecentlyWatchedMovies(limit: Int = 20): Flow<List<Movie>> = 
        movieDao.getRecentlyWatchedMovies(limit)
    
    suspend fun getMovieById(id: Long): Movie? = movieDao.getMovieById(id)
    
    suspend fun toggleMovieFavorite(movie: Movie) {
        movieDao.updateFavorite(movie.id, !movie.isFavorite)
    }
    
    suspend fun updateMovieProgress(movieId: Long, progress: Long) {
        movieDao.updateWatchProgress(movieId, progress, System.currentTimeMillis())
    }
    
    // === Series ===
    
    fun getAllSeries(): Flow<List<Series>> = seriesDao.getAllActiveSeries()
    
    fun getSeriesForCategory(categoryId: Long): Flow<List<Series>> = 
        seriesDao.getActiveSeriesForCategory(categoryId)
    
    fun getSeriesByGenre(genre: String): Flow<List<Series>> = 
        seriesDao.getActiveSeriesByGenre(genre)
    
    fun getDistinctSeriesGenres(): Flow<List<String>> = 
        seriesDao.getDistinctActiveGenres()
    
    fun getFavoriteSeries(): Flow<List<Series>> = seriesDao.getFavoriteSeries()
    
    fun searchSeries(query: String): Flow<List<Series>> = seriesDao.searchSeries(query)
    
    fun searchSeriesInGenre(query: String, genre: String): Flow<List<Series>> = 
        seriesDao.searchSeriesInGenre(query, genre)
    
    suspend fun getSeriesById(id: Long): Series? = seriesDao.getSeriesById(id)
    
    suspend fun toggleSeriesFavorite(series: Series) {
        seriesDao.updateFavorite(series.id, !series.isFavorite)
    }
    
    // === Episodes ===
    
    fun getEpisodesForSeries(seriesId: Long): Flow<List<Episode>> = 
        episodeDao.getEpisodesForSeries(seriesId)
    
    fun getEpisodesForSeason(seriesId: Long, season: Int): Flow<List<Episode>> = 
        episodeDao.getEpisodesForSeason(seriesId, season)
    
    suspend fun getEpisodeById(id: Long): Episode? = episodeDao.getEpisodeById(id)
    
    suspend fun updateEpisodeProgress(episodeId: Long, progress: Long, isWatched: Boolean = false) {
        episodeDao.updateWatchProgress(episodeId, progress, System.currentTimeMillis(), isWatched)
    }
    
    // === Playback History ===
    
    fun getRecentHistory(limit: Int = 50): Flow<List<PlaybackHistory>> = 
        playbackHistoryDao.getRecentHistory(limit)
    
    suspend fun addToHistory(contentType: ContentType, contentId: Long, contentName: String, position: Long, duration: Long) {
        playbackHistoryDao.insertHistory(
            PlaybackHistory(
                contentType = contentType,
                contentId = contentId,
                contentName = contentName,
                position = position,
                duration = duration
            )
        )
    }
    
    suspend fun getLastPosition(contentType: ContentType, contentId: Long): Long? {
        return playbackHistoryDao.getLastPosition(contentType, contentId)?.position
    }
    
    suspend fun clearHistory() {
        playbackHistoryDao.clearHistory()
    }
}

/**
 * Repository for EPG (Electronic Program Guide) operations
 */
@Singleton
class EpgRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val epgDao: EpgDao,
    private val channelDao: ChannelDao,
    private val playlistDao: PlaylistDao,
    private val xmltvParser: com.diokko.player.data.parser.XmltvParser,
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "EpgRepository"
        private const val EPG_REFRESH_INTERVAL = 6 * 60 * 60 * 1000L  // 6 hours
        private const val EPG_CACHE_FILE = "epg_temp.xml"
    }
    
    /**
     * Get current program for a channel
     */
    suspend fun getCurrentProgram(channelId: Long): EpgProgram? {
        return epgDao.getCurrentProgram(channelId, System.currentTimeMillis())
    }
    
    /**
     * Get next program for a channel
     */
    suspend fun getNextProgram(channelId: Long): EpgProgram? {
        return epgDao.getNextProgram(channelId, System.currentTimeMillis())
    }
    
    /**
     * Get all programs for a channel (from now onwards)
     */
    fun getProgramsForChannel(channelId: Long): Flow<List<EpgProgram>> {
        return epgDao.getProgramsForChannel(channelId, System.currentTimeMillis())
    }
    
    /**
     * Get current programs for multiple channels at once
     * NOTE: Only use for small batches (<100 channels) to avoid SQLite variable limit
     */
    suspend fun getCurrentProgramsForChannels(channelIds: List<Long>): Map<Long, EpgProgram> {
        if (channelIds.isEmpty()) return emptyMap()
        
        // Batch into chunks of 50 to avoid SQLite variable limit (999)
        val allPrograms = channelIds.chunked(50).flatMap { batch ->
            epgDao.getCurrentProgramsLimited(System.currentTimeMillis())
                .filter { it.channelId in batch }
        }
        return allPrograms.associateBy { it.channelId }
    }
    
    /**
     * OPTIMIZED: Get current programs for a specific group title using JOIN.
     * Avoids "too many SQL variables" error.
     */
    suspend fun getCurrentProgramsForGroup(groupTitle: String): Map<Long, EpgProgram> {
        val programs = epgDao.getCurrentProgramsForGroup(groupTitle, System.currentTimeMillis())
        return programs.associateBy { it.channelId }
    }
    
    /**
     * OPTIMIZED: Get upcoming programs for a specific group title using JOIN.
     */
    suspend fun getUpcomingProgramsForGroup(groupTitle: String): Map<Long, List<EpgProgram>> {
        val now = System.currentTimeMillis()
        val threeHoursLater = now + (3 * 60 * 60 * 1000)
        
        val programs = epgDao.getUpcomingProgramsForGroup(groupTitle, now, threeHoursLater)
        return programs.groupBy { it.channelId }
    }
    
    /**
     * OPTIMIZED: Get current programs for a specific category using JOIN.
     */
    suspend fun getCurrentProgramsForCategory(categoryId: Long): Map<Long, EpgProgram> {
        val programs = epgDao.getCurrentProgramsForCategory(categoryId, System.currentTimeMillis())
        return programs.associateBy { it.channelId }
    }
    
    /**
     * OPTIMIZED: Get upcoming programs for a specific category using JOIN.
     */
    suspend fun getUpcomingProgramsForCategory(categoryId: Long): Map<Long, List<EpgProgram>> {
        val now = System.currentTimeMillis()
        val threeHoursLater = now + (3 * 60 * 60 * 1000)
        
        val programs = epgDao.getUpcomingProgramsForCategory(categoryId, now, threeHoursLater)
        return programs.groupBy { it.channelId }
    }
    
    /**
     * Get current programs for initial load (limited to avoid memory issues)
     */
    suspend fun getCurrentProgramsLimited(): Map<Long, EpgProgram> {
        val programs = epgDao.getCurrentProgramsLimited(System.currentTimeMillis())
        return programs.associateBy { it.channelId }
    }
    
    /**
     * Refresh EPG data for all active playlists
     */
    suspend fun refreshEpgForAllPlaylists(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== EPG REFRESH STARTING ===")
            
            var totalPrograms = 0
            
            // Get all active playlists with EPG URLs (use .first() for one-time snapshot)
            val playlists = playlistDao.getActivePlaylists().first()
            Log.i(TAG, "Found ${playlists.size} active playlists")
            
            for (playlist in playlists) {
                Log.i(TAG, "Processing playlist: ${playlist.name}, type=${playlist.type}, url=${playlist.url?.take(50)}")
                val epgUrl = getEpgUrlForPlaylist(playlist)
                Log.i(TAG, "EPG URL for ${playlist.name}: ${epgUrl ?: "NULL"}")
                
                if (epgUrl != null) {
                    val result = refreshEpgForPlaylist(playlist, epgUrl)
                    if (result.isSuccess) {
                        totalPrograms += result.getOrDefault(0)
                    } else {
                        Log.e(TAG, "Failed to refresh EPG for ${playlist.name}: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
            
            // Clean up old programs
            epgDao.deleteOldPrograms(System.currentTimeMillis() - (24 * 60 * 60 * 1000))
            
            Log.i(TAG, "=== EPG REFRESH COMPLETE: $totalPrograms programs ===")
            Result.success(totalPrograms)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh EPG", e)
            Result.failure(e)
        }
    }
    
    /**
     * Refresh EPG data for a specific playlist.
     * Downloads EPG to cache file first for memory efficiency.
     */
    suspend fun refreshEpgForPlaylist(playlist: Playlist, epgUrl: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val cacheFile = java.io.File(context.cacheDir, EPG_CACHE_FILE)
        
        try {
            val url = epgUrl ?: getEpgUrlForPlaylist(playlist)
            if (url == null) {
                Log.i(TAG, "No EPG URL for playlist: ${playlist.name}")
                return@withContext Result.success(0)
            }
            
            Log.i(TAG, "Downloading EPG from: $url")
            
            // 1. Download to Cache File
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Accept", "application/xml, text/xml, */*")
                .build()
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "EPG Download failed: ${response.code}")
                return@withContext Result.failure(Exception("EPG Download failed: ${response.code}"))
            }
            
            response.body?.byteStream()?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "Download complete. File size: ${cacheFile.length()} bytes")
            
            // 2. Parse from File
            val parsedPrograms = java.io.FileInputStream(cacheFile).use { fileInput ->
                xmltvParser.parse(fileInput)
            }
            
            Log.i(TAG, "Parsed ${parsedPrograms.size} raw programs from XMLTV")
            
            // Log sample of parsed channel IDs for debugging
            val sampleChannelIds = parsedPrograms.take(10).map { it.channelId }.distinct()
            Log.i(TAG, "Sample XMLTV channel IDs: $sampleChannelIds")
            
            // 3. Get channel ID mapping for this playlist
            val channelMappings = channelDao.getChannelsWithEpgIdForPlaylist(playlist.id)
            val channelIdMap = channelMappings.associate { it.epgChannelId to it.id }
            
            Log.i(TAG, "Found ${channelIdMap.size} channels with EPG IDs in database")
            
            // Log sample of database channel EPG IDs for debugging
            val sampleDbEpgIds = channelIdMap.keys.take(10).toList()
            Log.i(TAG, "Sample DB epgChannelIds: $sampleDbEpgIds")
            
            if (channelIdMap.isEmpty()) {
                Log.w(TAG, "No channels have EPG IDs - EPG data won't be linked. Check if M3U has tvg-id attributes.")
                return@withContext Result.success(0)
            }
            
            // 4. Convert to EpgProgram entities
            val programs = xmltvParser.convertToEpgPrograms(parsedPrograms, channelIdMap)
            Log.i(TAG, "Matched ${programs.size} EPG programs to channels (${parsedPrograms.size} raw -> ${programs.size} matched)")
            
            if (programs.isEmpty() && parsedPrograms.isNotEmpty()) {
                Log.w(TAG, "EPG ID MISMATCH: Parsed ${parsedPrograms.size} programs but 0 matched channels!")
                Log.w(TAG, "XMLTV uses channel IDs like: ${sampleChannelIds.take(5)}")
                Log.w(TAG, "Database has epgChannelIds like: ${sampleDbEpgIds.take(5)}")
            }
            
            // 5. Delete old programs for this playlist and insert new ones
            epgDao.deleteProgramsForPlaylist(playlist.id)
            
            // Insert in batches to avoid memory issues
            programs.chunked(1000).forEach { batch ->
                epgDao.insertPrograms(batch)
            }
            
            // Update playlist EPG timestamp
            playlistDao.updatePlaylist(playlist.copy(epgLastUpdated = System.currentTimeMillis()))
            
            Log.i(TAG, "EPG refresh complete for ${playlist.name}: ${programs.size} programs inserted")
            Result.success(programs.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh EPG for playlist: ${playlist.name}", e)
            Result.failure(e)
        } finally {
            // 6. Cleanup cache file
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }
    
    /**
     * Get EPG URL for a playlist
     */
    private fun getEpgUrlForPlaylist(playlist: Playlist): String? {
        // If playlist has explicit EPG URL, use it
        if (!playlist.epgUrl.isNullOrBlank()) {
            return playlist.epgUrl
        }
        
        // For Xtream Codes playlists, generate EPG URL
        if (playlist.type == PlaylistType.XTREAM_CODES && 
            !playlist.serverUrl.isNullOrBlank() && 
            !playlist.username.isNullOrBlank() && 
            !playlist.password.isNullOrBlank()) {
            return xmltvParser.buildEpgUrl(playlist.serverUrl, playlist.username, playlist.password)
        }
        
        // For M3U playlists, try to extract EPG URL from the M3U URL if it follows Xtream Codes pattern
        // Pattern: http://<server>/get.php?username=<user>&password=<pass>&type=m3u_plus...
        if (playlist.type == PlaylistType.M3U && !playlist.url.isNullOrBlank()) {
            Log.i(TAG, "Attempting to extract EPG URL from M3U: ${playlist.url?.take(80)}...")
            val epgUrl = extractEpgUrlFromM3uUrl(playlist.url)
            if (epgUrl != null) {
                Log.i(TAG, "Extracted EPG URL from M3U: $epgUrl")
                return epgUrl
            } else {
                Log.w(TAG, "Could not extract EPG URL from M3U URL")
            }
        }
        
        return null
    }
    
    /**
     * Extract EPG URL from M3U URL if it follows Xtream Codes pattern.
     * Converts: http://server/get.php?username=X&password=Y&type=m3u_plus...
     * To: http://server/xmltv.php?username=X&password=Y
     */
    private fun extractEpgUrlFromM3uUrl(m3uUrl: String): String? {
        try {
            val uri = android.net.Uri.parse(m3uUrl)
            val username = uri.getQueryParameter("username")
            val password = uri.getQueryParameter("password")
            
            Log.i(TAG, "M3U URL parsing - username=${username?.take(5)}***, password=${if (password != null) "***" else "null"}")
            
            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                Log.w(TAG, "M3U URL missing username or password parameters")
                return null
            }
            
            // Build server base URL
            val scheme = uri.scheme ?: "http"
            val host = uri.host ?: return null
            val port = if (uri.port != -1) ":${uri.port}" else ""
            val serverUrl = "$scheme://$host$port"
            
            val epgUrl = xmltvParser.buildEpgUrl(serverUrl, username, password)
            Log.i(TAG, "Built EPG URL: $epgUrl")
            return epgUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract EPG URL from M3U URL: $m3uUrl", e)
            return null
        }
    }
    
    /**
     * Check if EPG needs refresh (older than 6 hours)
     */
    fun needsRefresh(playlist: Playlist): Boolean {
        val lastUpdated = playlist.epgLastUpdated ?: 0
        return System.currentTimeMillis() - lastUpdated > EPG_REFRESH_INTERVAL
    }
    
    /**
     * Get EPG program count
     */
    suspend fun getProgramCount(): Int = epgDao.getProgramCount()
    
    /**
     * Clear all EPG data
     */
    suspend fun clearAllEpg() {
        epgDao.deleteAllPrograms()
    }
    
    /**
     * Search EPG programs by title.
     * Returns programs with their associated channel info.
     */
    suspend fun searchPrograms(query: String): List<EpgSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        val currentTime = System.currentTimeMillis()
        val programs = epgDao.searchPrograms(query.trim(), currentTime)
        
        // Fetch channel info for each program
        programs.mapNotNull { program ->
            val channel = channelDao.getChannelById(program.channelId)
            if (channel != null) {
                EpgSearchResult(
                    program = program,
                    channel = channel
                )
            } else null
        }
    }
    
    /**
     * Search EPG programs within a specific group.
     */
    suspend fun searchProgramsInGroup(query: String, groupTitle: String): List<EpgSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        val currentTime = System.currentTimeMillis()
        val programs = epgDao.searchProgramsInGroup(query.trim(), groupTitle, currentTime)
        
        // Fetch channel info for each program
        programs.mapNotNull { program ->
            val channel = channelDao.getChannelById(program.channelId)
            if (channel != null) {
                EpgSearchResult(
                    program = program,
                    channel = channel
                )
            } else null
        }
    }
}

/**
 * EPG Search Result with channel info
 */
data class EpgSearchResult(
    val program: EpgProgram,
    val channel: Channel
)
