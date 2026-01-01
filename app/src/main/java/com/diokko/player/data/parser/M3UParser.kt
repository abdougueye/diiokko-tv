package com.diokko.player.data.parser

import android.util.Log
import com.diokko.player.data.models.Channel
import com.diokko.player.data.models.ContentType
import com.diokko.player.data.models.Episode
import com.diokko.player.data.models.Movie
import com.diokko.player.data.models.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "M3UParser"

/**
 * Parsed content from M3U playlist
 */
data class M3UContent(
    val channels: List<Channel>,
    val movies: List<Movie>,
    val series: List<Series>,
    val groups: Set<String>
)

/**
 * M3U Entry representing a single item in the playlist
 */
data class M3UEntry(
    val name: String,
    val url: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val tvgId: String?,
    val tvgName: String?,
    val tvgLogo: String?,
    val contentType: ContentType
)

/**
 * Parser for M3U/M3U8 playlist files
 * Ultra-optimized for large playlists (100k+ entries)
 * Uses string operations instead of regex for maximum speed
 */
@Singleton
class M3UParser @Inject constructor() {

    companion object {
        // Performance tuning constants
        private const val BATCH_SIZE = 5000
        private const val YIELD_INTERVAL = 100000  // Yield less frequently
        private const val BUFFER_SIZE = 262144  // 256KB buffer
    }

    /**
     * Result of streaming parse
     */
    data class StreamingParseResult(
        val channelCount: Int,
        val movieCount: Int,
        val seriesCount: Int,
        val groupCount: Int
    )
    
    /**
     * Callback interface for inserting entries during parsing
     */
    interface ParseCallback {
        suspend fun onChannelBatch(channels: List<Channel>)
        suspend fun onMovieBatch(movies: List<Movie>)
        /** Insert series and return map of series name to assigned ID */
        suspend fun onSeriesBatch(series: List<Series>): Map<String, Long>
        suspend fun onEpisodeBatch(episodes: List<Episode>)
        suspend fun onGroupsFound(groups: Set<String>)
    }

    /**
     * Ultra-fast attribute extraction using indexOf/substring instead of regex
     * Returns the value between quotes for a given attribute key
     */
    private inline fun extractAttribute(line: String, key: String): String? {
        val keyStart = line.indexOf(key)
        if (keyStart == -1) return null
        
        val valueStart = keyStart + key.length
        if (valueStart >= line.length) return null
        
        // Find the closing quote
        val valueEnd = line.indexOf('"', valueStart)
        if (valueEnd == -1) return null
        
        val value = line.substring(valueStart, valueEnd)
        return if (value.isEmpty()) null else value
    }
    
    /**
     * Check if entry is a divider/placeholder
     * Dividers have #### patterns and empty tvg-id
     */
    private inline fun isDividerEntry(name: String, tvgId: String?): Boolean {
        // Dividers typically have empty tvg-id and name with #####
        if (tvgId != null && tvgId.isNotEmpty()) return false
        return name.indexOf("####") != -1
    }
    
    /**
     * Extract display name from tvg-name attribute
     */
    private fun extractDisplayName(line: String): String {
        // Use tvg-name as the display name
        val tvgName = extractAttribute(line, "tvg-name=\"")
        if (tvgName != null) return tvgName
        
        // Fallback to text after last comma if tvg-name not found
        val lastComma = line.lastIndexOf(',')
        return if (lastComma != -1 && lastComma < line.length - 1) {
            line.substring(lastComma + 1)
        } else {
            "Unknown"
        }
    }
    
    /**
     * Fast content type detection using indexOf instead of regex
     */
    private fun determineContentType(url: String, groupTitle: String?, displayName: String? = null): ContentType {
        // Check URL path patterns (most reliable)
        if (url.indexOf("/movie/") != -1) return ContentType.MOVIE
        if (url.indexOf("/series/") != -1) return ContentType.SERIES
        
        // Check group title for movie/series keywords
        if (groupTitle != null) {
            val groupLower = groupTitle.lowercase()
            // Movie keywords
            if (groupLower.indexOf("vod") != -1 || 
                groupLower.indexOf("movie") != -1 || 
                groupLower.indexOf("film") != -1) {
                return ContentType.MOVIE
            }
            // Series keywords  
            if (groupLower.indexOf("series") != -1 || 
                groupLower.indexOf("serie") != -1) {
                return ContentType.SERIES
            }
        }
        
        // Check title for episode patterns (S##E## or S## E##)
        if (displayName != null) {
            var i = 0
            while (i < displayName.length - 4) {
                val c = displayName[i]
                if ((c == 'S' || c == 's') && i + 1 < displayName.length && displayName[i + 1].isDigit()) {
                    // Found S followed by digit, look for E pattern
                    var j = i + 1
                    while (j < displayName.length && displayName[j].isDigit()) j++
                    // Skip optional space
                    if (j < displayName.length && displayName[j] == ' ') j++
                    // Check for E followed by digit
                    if (j < displayName.length && (displayName[j] == 'E' || displayName[j] == 'e') &&
                        j + 1 < displayName.length && displayName[j + 1].isDigit()) {
                        return ContentType.SERIES
                    }
                }
                i++
            }
        }
        
        return ContentType.LIVE_TV
    }

    /**
     * Parse M3U content from input stream with direct database insertion
     * Ultra-optimized version using string operations
     */
    suspend fun parseStreaming(
        inputStream: InputStream, 
        playlistId: Long,
        callback: ParseCallback
    ): StreamingParseResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting optimized M3U parse for playlist $playlistId")
        val startTime = System.currentTimeMillis()
        
        val channelBatch = ArrayList<Channel>(BATCH_SIZE)
        val movieBatch = ArrayList<Movie>(BATCH_SIZE)
        val seriesBatch = ArrayList<Series>(BATCH_SIZE)
        val allGroups = HashSet<String>(500)
        val seenSeriesNames = HashSet<String>(10000)
        
        // Track series name to ID mapping (populated when series are inserted)
        val seriesNameToId = HashMap<String, Long>()
        
        // Pending episodes waiting for their series to be inserted
        // Stored as: Pair(seriesName, episode data without seriesId)
        data class PendingEpisode(
            val seriesName: String,
            val name: String,
            val streamUrl: String,
            val season: Int,
            val episodeNum: Int,
            val posterUrl: String?,
            val genre: String?
        )
        val pendingEpisodes = ArrayList<PendingEpisode>(BATCH_SIZE)
        val episodeBatch = ArrayList<Episode>(BATCH_SIZE)
        
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), BUFFER_SIZE)
        
        var currentExtInf: String? = null
        var lineCount = 0
        var entryCount = 0
        var channelCount = 0
        var movieCount = 0
        var seriesCount = 0
        var episodeCount = 0
        
        // Pre-computed attribute keys for faster extraction
        val TVG_ID_KEY = "tvg-id=\""
        val TVG_NAME_KEY = "tvg-name=\""
        val TVG_LOGO_KEY = "tvg-logo=\""
        val GROUP_TITLE_KEY = "group-title=\""
        
        suspend fun flushEpisodes() {
            if (pendingEpisodes.isEmpty()) return
            
            // Convert pending episodes to actual episodes using seriesNameToId map
            for (pending in pendingEpisodes) {
                val seriesId = seriesNameToId[pending.seriesName]
                if (seriesId != null) {
                    episodeBatch.add(
                        Episode(
                            seriesId = seriesId,
                            name = pending.name,
                            streamUrl = pending.streamUrl,
                            season = pending.season,
                            episodeNum = pending.episodeNum,
                            posterUrl = pending.posterUrl
                        )
                    )
                }
            }
            pendingEpisodes.clear()
            
            // Flush episode batch if large enough
            if (episodeBatch.size >= BATCH_SIZE) {
                callback.onEpisodeBatch(episodeBatch.toList())
                episodeBatch.clear()
            }
        }
        
        suspend fun flushBatches(force: Boolean = false) {
            if (channelBatch.size >= BATCH_SIZE || (force && channelBatch.isNotEmpty())) {
                callback.onChannelBatch(channelBatch.toList())
                channelBatch.clear()
            }
            if (movieBatch.size >= BATCH_SIZE || (force && movieBatch.isNotEmpty())) {
                callback.onMovieBatch(movieBatch.toList())
                movieBatch.clear()
            }
            if (seriesBatch.size >= BATCH_SIZE || (force && seriesBatch.isNotEmpty())) {
                // Insert series and get back name→ID mapping
                val newMappings = callback.onSeriesBatch(seriesBatch.toList())
                seriesNameToId.putAll(newMappings)
                seriesBatch.clear()
                
                // Now we can flush pending episodes that were waiting for series IDs
                flushEpisodes()
            }
            
            // Force flush remaining episodes
            if (force && episodeBatch.isNotEmpty()) {
                callback.onEpisodeBatch(episodeBatch.toList())
                episodeBatch.clear()
            }
        }
        
        try {
            var line = reader.readLine()
            while (line != null) {
                lineCount++
                
                // Yield periodically to keep UI responsive
                if (lineCount % YIELD_INTERVAL == 0) {
                    kotlinx.coroutines.yield()
                }
                
                // Skip empty lines quickly
                if (line.isEmpty()) {
                    line = reader.readLine()
                    continue
                }
                
                // Check for EXTINF line
                if (line.startsWith("#EXTINF:")) {
                    currentExtInf = line
                }
                // Check for URL line (not starting with #)
                else if (line[0] != '#' && currentExtInf != null) {
                    // Extract attributes using fast string operations
                    val tvgId = extractAttribute(currentExtInf!!, TVG_ID_KEY)
                    val tvgLogo = extractAttribute(currentExtInf!!, TVG_LOGO_KEY)
                    val groupTitle = extractAttribute(currentExtInf!!, GROUP_TITLE_KEY) ?: "Other"
                    // Use tvg-name as displayName
                    val displayName = extractAttribute(currentExtInf!!, TVG_NAME_KEY) ?: extractDisplayName(currentExtInf!!)
                    val url = line.trim()
                    
                    if (url.isNotEmpty()) {
                        entryCount++
                        allGroups.add(groupTitle)
                        
                        val isDivider = isDividerEntry(displayName, tvgId)
                        val contentType = determineContentType(url, groupTitle, displayName)
                        
                        when (contentType) {
                            ContentType.LIVE_TV, ContentType.UNKNOWN -> {
                                channelBatch.add(
                                    Channel(
                                        playlistId = playlistId,
                                        name = displayName,
                                        streamUrl = url,
                                        logoUrl = tvgLogo,
                                        groupTitle = groupTitle,
                                        epgChannelId = tvgId,
                                        order = entryCount,
                                        isDivider = isDivider
                                    )
                                )
                                channelCount++
                            }
                            ContentType.MOVIE -> {
                                movieBatch.add(
                                    Movie(
                                        playlistId = playlistId,
                                        name = displayName,
                                        streamUrl = url,
                                        posterUrl = tvgLogo,
                                        genre = groupTitle
                                    )
                                )
                                movieCount++
                            }
                            ContentType.SERIES -> {
                                // Extract series info (name, season, episode)
                                val seriesInfo = extractSeriesInfo(displayName)
                                
                                if (seriesInfo != null) {
                                    // We have proper season/episode info
                                    val seriesName = seriesInfo.seriesName
                                    
                                    // Add series if not seen before
                                    if (seriesName !in seenSeriesNames) {
                                        seenSeriesNames.add(seriesName)
                                        seriesBatch.add(
                                            Series(
                                                playlistId = playlistId,
                                                name = seriesName,
                                                posterUrl = tvgLogo,
                                                genre = groupTitle
                                            )
                                        )
                                        seriesCount++
                                    }
                                    
                                    // Add episode to pending list
                                    pendingEpisodes.add(
                                        PendingEpisode(
                                            seriesName = seriesName,
                                            name = displayName,
                                            streamUrl = url,
                                            season = seriesInfo.season,
                                            episodeNum = seriesInfo.episode,
                                            posterUrl = tvgLogo,
                                            genre = groupTitle
                                        )
                                    )
                                    episodeCount++
                                } else {
                                    // No episode pattern found, treat as series entry without episode info
                                    val seriesName = displayName
                                    if (seriesName !in seenSeriesNames) {
                                        seenSeriesNames.add(seriesName)
                                        seriesBatch.add(
                                            Series(
                                                playlistId = playlistId,
                                                name = seriesName,
                                                posterUrl = tvgLogo,
                                                genre = groupTitle
                                            )
                                        )
                                        seriesCount++
                                    }
                                }
                            }
                        }
                        
                        // Flush when batch is full
                        flushBatches(force = false)
                    }
                    currentExtInf = null
                }
                
                line = reader.readLine()
            }
            
            // Flush remaining series first (to get IDs)
            if (seriesBatch.isNotEmpty()) {
                val newMappings = callback.onSeriesBatch(seriesBatch.toList())
                seriesNameToId.putAll(newMappings)
                seriesBatch.clear()
            }
            
            // Flush remaining pending episodes
            flushEpisodes()
            
            // Flush any remaining items
            flushBatches(force = true)
            callback.onGroupsFound(allGroups)
            
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            throw e
        } finally {
            try { reader.close() } catch (_: Exception) {}
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Parse complete in ${elapsed}ms: $channelCount channels, $movieCount movies, $seriesCount series, $episodeCount episodes")
        
        StreamingParseResult(channelCount, movieCount, seriesCount, allGroups.size)
    }
    
    /**
     * Extract series name from episode title using fast string operations
     * Also returns season and episode numbers if found
     */
    private data class SeriesInfo(
        val seriesName: String,
        val season: Int,
        val episode: Int
    )
    
    private fun extractSeriesInfo(title: String): SeriesInfo? {
        // Pattern 1: S##E## or S## E## - look for S followed by digit
        var i = 0
        while (i < title.length - 4) {
            val c = title[i]
            if ((c == 'S' || c == 's') && i + 1 < title.length && title[i + 1].isDigit()) {
                // Found potential season marker
                val seasonStart = i + 1
                var seasonEnd = seasonStart
                while (seasonEnd < title.length && title[seasonEnd].isDigit()) {
                    seasonEnd++
                }
                
                if (seasonEnd > seasonStart) {
                    val season = title.substring(seasonStart, seasonEnd).toIntOrNull() ?: 0
                    
                    // Look for E## pattern after season (with or without space)
                    var ePos = seasonEnd
                    if (ePos < title.length && title[ePos] == ' ') ePos++
                    
                    if (ePos < title.length && (title[ePos] == 'E' || title[ePos] == 'e')) {
                        val episodeStart = ePos + 1
                        var episodeEnd = episodeStart
                        while (episodeEnd < title.length && title[episodeEnd].isDigit()) {
                            episodeEnd++
                        }
                        
                        if (episodeEnd > episodeStart) {
                            val episode = title.substring(episodeStart, episodeEnd).toIntOrNull() ?: 0
                            val seriesName = title.substring(0, i).trim()
                            if (seriesName.isNotEmpty() && season > 0 && episode > 0) {
                                return SeriesInfo(seriesName, season, episode)
                            }
                        }
                    }
                }
            }
            i++
        }
        
        // Pattern 2: ##x## - find digit followed by 'x' and digit
        for (j in 0 until title.length - 4) {
            if (title[j].isDigit()) {
                // Find all consecutive digits for season
                var seasonEnd = j
                while (seasonEnd < title.length && title[seasonEnd].isDigit()) {
                    seasonEnd++
                }
                
                if (seasonEnd < title.length && title[seasonEnd] == 'x' && 
                    seasonEnd + 1 < title.length && title[seasonEnd + 1].isDigit()) {
                    val season = title.substring(j, seasonEnd).toIntOrNull() ?: 0
                    
                    val episodeStart = seasonEnd + 1
                    var episodeEnd = episodeStart
                    while (episodeEnd < title.length && title[episodeEnd].isDigit()) {
                        episodeEnd++
                    }
                    
                    val episode = title.substring(episodeStart, episodeEnd).toIntOrNull() ?: 0
                    val seriesName = title.substring(0, j).trim()
                    if (seriesName.isNotEmpty() && season > 0 && episode > 0) {
                        return SeriesInfo(seriesName, season, episode)
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Extract just series name (legacy method for compatibility)
     */
    private fun extractSeriesName(title: String): String {
        val info = extractSeriesInfo(title)
        return info?.seriesName ?: title
    }

    /**
     * Parse M3U content from string (legacy method)
     */
    suspend fun parse(content: String, playlistId: Long): M3UContent = withContext(Dispatchers.IO) {
        val channels = mutableListOf<Channel>()
        val movies = mutableListOf<Movie>()
        val series = mutableListOf<Series>()
        val groups = mutableSetOf<String>()
        
        val lines = content.lines()
        var currentExtInf: String? = null
        var entryCount = 0
        
        val TVG_ID_KEY = "tvg-id=\""
        val TVG_NAME_KEY = "tvg-name=\""
        val TVG_LOGO_KEY = "tvg-logo=\""
        val GROUP_TITLE_KEY = "group-title=\""
        
        for (line in lines) {
            if (line.isEmpty()) continue
            
            if (line.startsWith("#EXTINF:")) {
                currentExtInf = line
            } else if (line[0] != '#' && currentExtInf != null) {
                val tvgId = extractAttribute(currentExtInf, TVG_ID_KEY)
                val tvgLogo = extractAttribute(currentExtInf, TVG_LOGO_KEY)
                val groupTitle = extractAttribute(currentExtInf, GROUP_TITLE_KEY) ?: "Other"
                val displayName = extractAttribute(currentExtInf, TVG_NAME_KEY) ?: extractDisplayName(currentExtInf)
                val url = line.trim()
                
                if (url.isNotEmpty()) {
                    entryCount++
                    groups.add(groupTitle)
                    
                    val contentType = determineContentType(url, groupTitle, displayName)
                    
                    when (contentType) {
                        ContentType.LIVE_TV, ContentType.UNKNOWN -> {
                            channels.add(
                                Channel(
                                    playlistId = playlistId,
                                    name = displayName,
                                    streamUrl = url,
                                    logoUrl = tvgLogo,
                                    groupTitle = groupTitle,
                                    epgChannelId = tvgId,
                                    order = entryCount
                                )
                            )
                        }
                        ContentType.MOVIE -> {
                            movies.add(
                                Movie(
                                    playlistId = playlistId,
                                    name = displayName,
                                    streamUrl = url,
                                    posterUrl = tvgLogo,
                                    genre = groupTitle
                                )
                            )
                        }
                        ContentType.SERIES -> {
                            series.add(
                                Series(
                                    playlistId = playlistId,
                                    name = extractSeriesName(displayName),
                                    posterUrl = tvgLogo,
                                    genre = groupTitle
                                )
                            )
                        }
                    }
                }
                currentExtInf = null
            }
        }
        
        M3UContent(channels, movies, series.distinctBy { it.name }, groups)
    }
}

