package com.diokko.player.data.database

import androidx.room.*
import com.diokko.player.data.models.*
import kotlinx.coroutines.flow.Flow

/**
 * Type converters for Room database
 */
class Converters {
    @TypeConverter
    fun fromPlaylistType(type: PlaylistType): String = type.name

    @TypeConverter
    fun toPlaylistType(value: String): PlaylistType = PlaylistType.valueOf(value)

    @TypeConverter
    fun fromContentType(type: ContentType): String = type.name

    @TypeConverter
    fun toContentType(value: String): ContentType = ContentType.valueOf(value)
}

/**
 * Playlist Data Access Object
 */
@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE isActive = 1 ORDER BY name ASC")
    fun getActivePlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Query("UPDATE playlists SET lastUpdated = :timestamp, channelCount = :channels, movieCount = :movies, seriesCount = :series WHERE id = :playlistId")
    suspend fun updatePlaylistStats(playlistId: Long, timestamp: Long, channels: Int, movies: Int, series: Int)
}

/**
 * Category Data Access Object
 */
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type ORDER BY `order` ASC, name ASC")
    fun getCategoriesByType(playlistId: Long, type: ContentType): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId ORDER BY `order` ASC, name ASC")
    fun getAllCategoriesForPlaylist(playlistId: Long): Flow<List<Category>>

    @Query("SELECT DISTINCT c.* FROM categories c INNER JOIN playlists p ON c.playlistId = p.id WHERE p.isActive = 1 AND c.type = :type ORDER BY c.name ASC")
    fun getAllActiveCategoriesByType(type: ContentType): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteCategoriesForPlaylist(playlistId: Long)
}

/**
 * Channel Data Access Object
 */
@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY `order` ASC, name ASC")
    fun getChannelsForPlaylist(playlistId: Long): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId ORDER BY `order` ASC, name ASC")
    fun getChannelsForCategory(categoryId: Long): Flow<List<Channel>>

    @Query("SELECT c.* FROM channels c INNER JOIN playlists p ON c.playlistId = p.id WHERE p.isActive = 1 ORDER BY c.name ASC")
    fun getAllActiveChannels(): Flow<List<Channel>>

    @Query("SELECT c.* FROM channels c INNER JOIN playlists p ON c.playlistId = p.id WHERE p.isActive = 1 AND c.categoryId = :categoryId ORDER BY c.`order` ASC, c.name ASC")
    fun getActiveChannelsForCategory(categoryId: Long): Flow<List<Channel>>
    
    @Query("SELECT c.* FROM channels c INNER JOIN playlists p ON c.playlistId = p.id WHERE p.isActive = 1 AND c.groupTitle = :groupTitle ORDER BY c.`order` ASC, c.name ASC")
    fun getActiveChannelsByGroupTitle(groupTitle: String): Flow<List<Channel>>
    
    @Query("SELECT DISTINCT c.groupTitle FROM channels c INNER JOIN playlists p ON c.playlistId = p.id WHERE p.isActive = 1 AND c.groupTitle IS NOT NULL ORDER BY c.groupTitle ASC")
    fun getDistinctActiveGroups(): Flow<List<String>>

    @Query("SELECT c.* FROM channels c INNER JOIN playlists p ON c.playlistId = p.id WHERE p.isActive = 1 AND c.isFavorite = 1 ORDER BY c.name ASC")
    fun getFavoriteChannels(): Flow<List<Channel>>

    @Query("SELECT c.* FROM channels c INNER JOIN playlists p ON c.playlistId = p.id WHERE p.isActive = 1 AND c.name LIKE '%' || :query || '%' ORDER BY c.name ASC")
    fun searchChannels(query: String): Flow<List<Channel>>
    
    @Query("SELECT c.* FROM channels c INNER JOIN playlists p ON c.playlistId = p.id WHERE p.isActive = 1 AND c.groupTitle = :groupTitle AND c.name LIKE '%' || :query || '%' ORDER BY c.`order` ASC, c.name ASC")
    fun searchChannelsInCategory(query: String, groupTitle: String): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getChannelById(id: Long): Channel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: Channel): Long

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)

    @Update
    suspend fun updateChannel(channel: Channel)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE channels SET lastWatched = :timestamp WHERE id = :id")
    suspend fun updateLastWatched(id: Long, timestamp: Long)

    @Delete
    suspend fun deleteChannel(channel: Channel)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsForPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun getChannelCountForPlaylist(playlistId: Long): Int
    
    @Query("SELECT c.id, c.epgChannelId FROM channels c INNER JOIN playlists p ON c.playlistId = p.id WHERE p.isActive = 1 AND c.epgChannelId IS NOT NULL")
    suspend fun getActiveChannelsWithEpgId(): List<ChannelEpgMapping>
    
    @Query("SELECT c.id, c.epgChannelId FROM channels c WHERE c.playlistId = :playlistId AND c.epgChannelId IS NOT NULL AND c.epgChannelId != ''")
    suspend fun getChannelsWithEpgIdForPlaylist(playlistId: Long): List<ChannelEpgMapping>
}

/**
 * Helper class for EPG channel mapping
 */
data class ChannelEpgMapping(
    val id: Long,
    val epgChannelId: String
)

/**
 * Movie Data Access Object
 */
@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY name ASC")
    fun getMoviesForPlaylist(playlistId: Long): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getMoviesForCategory(categoryId: Long): Flow<List<Movie>>

    @Query("SELECT m.* FROM movies m INNER JOIN playlists p ON m.playlistId = p.id WHERE p.isActive = 1 ORDER BY m.name ASC")
    fun getAllActiveMovies(): Flow<List<Movie>>

    @Query("SELECT m.* FROM movies m INNER JOIN playlists p ON m.playlistId = p.id WHERE p.isActive = 1 AND m.categoryId = :categoryId ORDER BY m.name ASC")
    fun getActiveMoviesForCategory(categoryId: Long): Flow<List<Movie>>
    
    @Query("SELECT m.* FROM movies m INNER JOIN playlists p ON m.playlistId = p.id WHERE p.isActive = 1 AND m.genre = :genre ORDER BY m.name ASC")
    fun getActiveMoviesByGenre(genre: String): Flow<List<Movie>>
    
    @Query("SELECT DISTINCT m.genre FROM movies m INNER JOIN playlists p ON m.playlistId = p.id WHERE p.isActive = 1 AND m.genre IS NOT NULL ORDER BY m.genre ASC")
    fun getDistinctActiveGenres(): Flow<List<String>>

    @Query("SELECT m.* FROM movies m INNER JOIN playlists p ON m.playlistId = p.id WHERE p.isActive = 1 AND m.isFavorite = 1 ORDER BY m.name ASC")
    fun getFavoriteMovies(): Flow<List<Movie>>

    @Query("SELECT m.* FROM movies m INNER JOIN playlists p ON m.playlistId = p.id WHERE p.isActive = 1 AND m.name LIKE '%' || :query || '%' ORDER BY m.name ASC")
    fun searchMovies(query: String): Flow<List<Movie>>
    
    @Query("SELECT m.* FROM movies m INNER JOIN playlists p ON m.playlistId = p.id WHERE p.isActive = 1 AND m.genre = :genre AND m.name LIKE '%' || :query || '%' ORDER BY m.name ASC")
    fun searchMoviesInGenre(query: String, genre: String): Flow<List<Movie>>

    @Query("SELECT m.* FROM movies m INNER JOIN playlists p ON m.playlistId = p.id WHERE p.isActive = 1 ORDER BY m.lastWatched DESC LIMIT :limit")
    fun getRecentlyWatchedMovies(limit: Int = 20): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun getMovieById(id: Long): Movie?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: Movie): Long

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<Movie>)

    @Update
    suspend fun updateMovie(movie: Movie)

    @Query("UPDATE movies SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE movies SET watchProgress = :progress, lastWatched = :timestamp WHERE id = :id")
    suspend fun updateWatchProgress(id: Long, progress: Long, timestamp: Long)

    @Delete
    suspend fun deleteMovie(movie: Movie)

    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun deleteMoviesForPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId")
    suspend fun getMovieCountForPlaylist(playlistId: Long): Int
}

/**
 * Series Data Access Object
 */
@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY name ASC")
    fun getSeriesForPlaylist(playlistId: Long): Flow<List<Series>>

    @Query("SELECT * FROM series WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getSeriesForCategory(categoryId: Long): Flow<List<Series>>

    @Query("SELECT s.* FROM series s INNER JOIN playlists p ON s.playlistId = p.id WHERE p.isActive = 1 ORDER BY s.name ASC")
    fun getAllActiveSeries(): Flow<List<Series>>

    @Query("SELECT s.* FROM series s INNER JOIN playlists p ON s.playlistId = p.id WHERE p.isActive = 1 AND s.categoryId = :categoryId ORDER BY s.name ASC")
    fun getActiveSeriesForCategory(categoryId: Long): Flow<List<Series>>
    
    @Query("SELECT s.* FROM series s INNER JOIN playlists p ON s.playlistId = p.id WHERE p.isActive = 1 AND s.genre = :genre ORDER BY s.name ASC")
    fun getActiveSeriesByGenre(genre: String): Flow<List<Series>>
    
    @Query("SELECT DISTINCT s.genre FROM series s INNER JOIN playlists p ON s.playlistId = p.id WHERE p.isActive = 1 AND s.genre IS NOT NULL ORDER BY s.genre ASC")
    fun getDistinctActiveGenres(): Flow<List<String>>

    @Query("SELECT s.* FROM series s INNER JOIN playlists p ON s.playlistId = p.id WHERE p.isActive = 1 AND s.isFavorite = 1 ORDER BY s.name ASC")
    fun getFavoriteSeries(): Flow<List<Series>>

    @Query("SELECT s.* FROM series s INNER JOIN playlists p ON s.playlistId = p.id WHERE p.isActive = 1 AND s.name LIKE '%' || :query || '%' ORDER BY s.name ASC")
    fun searchSeries(query: String): Flow<List<Series>>
    
    @Query("SELECT s.* FROM series s INNER JOIN playlists p ON s.playlistId = p.id WHERE p.isActive = 1 AND s.genre = :genre AND s.name LIKE '%' || :query || '%' ORDER BY s.name ASC")
    fun searchSeriesInGenre(query: String, genre: String): Flow<List<Series>>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getSeriesById(id: Long): Series?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: Series): Long

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeriesList(seriesList: List<Series>)

    @Update
    suspend fun updateSeries(series: Series)

    @Query("UPDATE series SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Delete
    suspend fun deleteSeries(series: Series)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteSeriesForPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    suspend fun getSeriesCountForPlaylist(playlistId: Long): Int
}

/**
 * Episode Data Access Object
 */
@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY season ASC, episodeNum ASC")
    fun getEpisodesForSeries(seriesId: Long): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND season = :season ORDER BY episodeNum ASC")
    fun getEpisodesForSeason(seriesId: Long, season: Int): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeById(id: Long): Episode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: Episode): Long

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<Episode>)

    @Update
    suspend fun updateEpisode(episode: Episode)

    @Query("UPDATE episodes SET watchProgress = :progress, lastWatched = :timestamp, isWatched = :isWatched WHERE id = :id")
    suspend fun updateWatchProgress(id: Long, progress: Long, timestamp: Long, isWatched: Boolean)

    @Delete
    suspend fun deleteEpisode(episode: Episode)

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteEpisodesForSeries(seriesId: Long)
    
    @Query("DELETE FROM episodes WHERE seriesId IN (SELECT id FROM series WHERE playlistId = :playlistId)")
    suspend fun deleteEpisodesForPlaylist(playlistId: Long)
}

/**
 * EPG Data Access Object
 */
@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND endTime > :currentTime ORDER BY startTime ASC")
    fun getProgramsForChannel(channelId: Long, currentTime: Long): Flow<List<EpgProgram>>

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime <= :currentTime AND endTime > :currentTime LIMIT 1")
    suspend fun getCurrentProgram(channelId: Long, currentTime: Long): EpgProgram?

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime > :currentTime ORDER BY startTime ASC LIMIT 1")
    suspend fun getNextProgram(channelId: Long, currentTime: Long): EpgProgram?
    
    @Query("""
        SELECT ep.* FROM epg_programs ep 
        INNER JOIN channels c ON ep.channelId = c.id 
        WHERE c.epgChannelId = :epgChannelId AND ep.endTime > :currentTime 
        ORDER BY ep.startTime ASC
    """)
    fun getProgramsByEpgChannelId(epgChannelId: String, currentTime: Long): Flow<List<EpgProgram>>
    
    @Query("""
        SELECT ep.* FROM epg_programs ep 
        INNER JOIN channels c ON ep.channelId = c.id 
        WHERE c.epgChannelId = :epgChannelId 
        AND ep.startTime <= :currentTime AND ep.endTime > :currentTime 
        LIMIT 1
    """)
    suspend fun getCurrentProgramByEpgChannelId(epgChannelId: String, currentTime: Long): EpgProgram?
    
    /**
     * OPTIMIZED: Fetch current programs for a specific GROUP TITLE using JOIN.
     * Avoids "too many SQL variables" error by not passing channel ID lists.
     */
    @Query("""
        SELECT ep.* FROM epg_programs ep
        INNER JOIN channels c ON ep.channelId = c.id
        WHERE c.groupTitle = :groupTitle
        AND ep.startTime <= :currentTime AND ep.endTime > :currentTime
        ORDER BY c.`order` ASC
    """)
    suspend fun getCurrentProgramsForGroup(groupTitle: String, currentTime: Long): List<EpgProgram>
    
    /**
     * OPTIMIZED: Fetch upcoming programs for a specific GROUP TITLE using JOIN.
     */
    @Query("""
        SELECT ep.* FROM epg_programs ep
        INNER JOIN channels c ON ep.channelId = c.id
        WHERE c.groupTitle = :groupTitle
        AND ep.startTime > :currentTime
        AND ep.startTime < :maxTime
        ORDER BY c.`order` ASC, ep.startTime ASC
    """)
    suspend fun getUpcomingProgramsForGroup(groupTitle: String, currentTime: Long, maxTime: Long): List<EpgProgram>
    
    /**
     * OPTIMIZED: Fetch current programs for a specific CATEGORY using JOIN.
     */
    @Query("""
        SELECT ep.* FROM epg_programs ep
        INNER JOIN channels c ON ep.channelId = c.id
        WHERE c.categoryId = :categoryId
        AND ep.startTime <= :currentTime AND ep.endTime > :currentTime
        ORDER BY c.`order` ASC
    """)
    suspend fun getCurrentProgramsForCategory(categoryId: Long, currentTime: Long): List<EpgProgram>
    
    /**
     * OPTIMIZED: Fetch upcoming programs for a specific CATEGORY using JOIN.
     */
    @Query("""
        SELECT ep.* FROM epg_programs ep
        INNER JOIN channels c ON ep.channelId = c.id
        WHERE c.categoryId = :categoryId
        AND ep.startTime > :currentTime
        AND ep.startTime < :maxTime
        ORDER BY c.`order` ASC, ep.startTime ASC
    """)
    suspend fun getUpcomingProgramsForCategory(categoryId: Long, currentTime: Long, maxTime: Long): List<EpgProgram>
    
    /**
     * Fetch current programs for ALL channels (limited batch for initial load).
     * Uses subquery to limit channels, avoiding "too many SQL variables".
     */
    @Query("""
        SELECT ep.* FROM epg_programs ep
        WHERE ep.channelId IN (SELECT id FROM channels WHERE isDivider = 0 LIMIT 100)
        AND ep.startTime <= :currentTime AND ep.endTime > :currentTime
    """)
    suspend fun getCurrentProgramsLimited(currentTime: Long): List<EpgProgram>

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgram>)

    @Query("DELETE FROM epg_programs WHERE channelId = :channelId")
    suspend fun deleteProgramsForChannel(channelId: Long)
    
    @Query("DELETE FROM epg_programs WHERE channelId IN (SELECT id FROM channels WHERE playlistId = :playlistId)")
    suspend fun deleteProgramsForPlaylist(playlistId: Long)

    @Query("DELETE FROM epg_programs WHERE endTime < :timestamp")
    suspend fun deleteOldPrograms(timestamp: Long)
    
    @Query("DELETE FROM epg_programs")
    suspend fun deleteAllPrograms()
    
    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun getProgramCount(): Int
    
    /**
     * Search EPG programs by title.
     * Returns programs with channel info for display.
     */
    @Query("""
        SELECT ep.* FROM epg_programs ep
        WHERE ep.title LIKE '%' || :query || '%'
        AND ep.endTime > :currentTime
        ORDER BY ep.startTime ASC
        LIMIT 100
    """)
    suspend fun searchPrograms(query: String, currentTime: Long): List<EpgProgram>
    
    /**
     * Search EPG programs within a specific group.
     */
    @Query("""
        SELECT ep.* FROM epg_programs ep
        INNER JOIN channels c ON ep.channelId = c.id
        WHERE ep.title LIKE '%' || :query || '%'
        AND c.groupTitle = :groupTitle
        AND ep.endTime > :currentTime
        ORDER BY ep.startTime ASC
        LIMIT 100
    """)
    suspend fun searchProgramsInGroup(query: String, groupTitle: String, currentTime: Long): List<EpgProgram>
}

/**
 * Playback History Data Access Object
 */
@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<PlaybackHistory>>

    @Query("SELECT * FROM playback_history WHERE contentType = :type AND contentId = :id ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPosition(type: ContentType, id: Long): PlaybackHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistory)

    @Query("DELETE FROM playback_history WHERE timestamp < :timestamp")
    suspend fun deleteOldHistory(timestamp: Long)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}

/**
 * Main Room Database
 */
@Database(
    entities = [
        Playlist::class,
        Category::class,
        Channel::class,
        Movie::class,
        Series::class,
        Episode::class,
        EpgProgram::class,
        PlaybackHistory::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DiokkoDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun epgDao(): EpgDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
}
