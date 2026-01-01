package com.diokko.player.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.diokko.player.data.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Custom logging interceptor that uses Log.w for visibility
        val customLogger = Interceptor { chain ->
            val request = chain.request()
            Log.w("HTTP", ">>> ${request.method} ${request.url}")
            
            val startTime = System.currentTimeMillis()
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            
            Log.w("HTTP", "<<< ${response.code} ${response.message} (${duration}ms)")
            Log.w("HTTP", "Content-Length: ${response.header("Content-Length") ?: "unknown"}")
            Log.w("HTTP", "Content-Type: ${response.header("Content-Type") ?: "unknown"}")
            
            response
        }
        
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.w("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)  // Increased for slow servers
            .readTimeout(120, TimeUnit.SECONDS)    // Increased for large playlists
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(customLogger)
            .addInterceptor(loggingInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    private const val TAG = "DiokkoDatabase"
    private const val DATABASE_NAME = "diokko_database"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DiokkoDatabase {
        // Check for and handle corrupted database before building
        handlePotentialCorruption(context)
        
        return Room.databaseBuilder(
            context,
            DiokkoDatabase::class.java,
            DATABASE_NAME
        )
            // Enable Write-Ahead Logging for better crash resistance
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // Handle migration failures gracefully
            .fallbackToDestructiveMigration()
            // Add callback for database lifecycle events
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Log.i(TAG, "Database created successfully")
                }
                
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Log.i(TAG, "Database opened successfully")
                    // Run integrity check on open
                    try {
                        val cursor = db.query("PRAGMA integrity_check")
                        if (cursor.moveToFirst()) {
                            val result = cursor.getString(0)
                            if (result != "ok") {
                                Log.e(TAG, "Database integrity check failed: $result")
                            } else {
                                Log.i(TAG, "Database integrity check passed")
                            }
                        }
                        cursor.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error running integrity check: ${e.message}")
                    }
                }
                
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    Log.w(TAG, "Database destructive migration occurred - data was reset")
                }
            })
            .build()
    }
    
    /**
     * Check if database file exists and appears corrupted, delete if necessary
     * This prevents crashes on startup due to corruption
     */
    private fun handlePotentialCorruption(context: Context) {
        val dbPath = context.getDatabasePath(DATABASE_NAME)
        val walPath = File(dbPath.path + "-wal")
        val shmPath = File(dbPath.path + "-shm")
        
        if (dbPath.exists()) {
            try {
                // Try to open the database directly to check if it's corrupted
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbPath.path,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                
                // Quick integrity check
                val cursor = db.rawQuery("PRAGMA quick_check", null)
                val isOk = cursor.moveToFirst() && cursor.getString(0) == "ok"
                cursor.close()
                db.close()
                
                if (!isOk) {
                    Log.e(TAG, "Database corruption detected during startup check, deleting...")
                    deleteCorruptedDatabase(dbPath, walPath, shmPath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Database appears corrupted: ${e.message}, deleting...")
                deleteCorruptedDatabase(dbPath, walPath, shmPath)
            }
        }
    }
    
    private fun deleteCorruptedDatabase(dbPath: File, walPath: File, shmPath: File) {
        try {
            dbPath.delete()
            walPath.delete()
            shmPath.delete()
            Log.w(TAG, "Corrupted database files deleted, will recreate on next access")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting corrupted database: ${e.message}")
        }
    }

    @Provides
    fun providePlaylistDao(database: DiokkoDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideCategoryDao(database: DiokkoDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideChannelDao(database: DiokkoDatabase): ChannelDao = database.channelDao()

    @Provides
    fun provideMovieDao(database: DiokkoDatabase): MovieDao = database.movieDao()

    @Provides
    fun provideSeriesDao(database: DiokkoDatabase): SeriesDao = database.seriesDao()

    @Provides
    fun provideEpisodeDao(database: DiokkoDatabase): EpisodeDao = database.episodeDao()

    @Provides
    fun provideEpgDao(database: DiokkoDatabase): EpgDao = database.epgDao()

    @Provides
    fun providePlaybackHistoryDao(database: DiokkoDatabase): PlaybackHistoryDao = database.playbackHistoryDao()
}
