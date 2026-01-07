package com.diokko.player

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.diokko.player.data.database.DiokkoDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DiokkoApp : Application() {
    
    companion object {
        private const val TAG = "DiokkoApp"
    }
    
    @Inject
    lateinit var database: DiokkoDatabase
    
    private var activityCount = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application starting...")
        
        // Use ActivityLifecycleCallbacks to track foreground/background
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                activityCount++
                if (activityCount == 1) {
                    Log.i(TAG, "App moved to foreground")
                }
            }
            
            override fun onActivityStopped(activity: Activity) {
                activityCount--
                if (activityCount == 0) {
                    Log.i(TAG, "App moved to background - checkpointing database...")
                    checkpointDatabase()
                }
            }
            
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
    
    /**
     * Force a WAL checkpoint to ensure all data is written to the main database file.
     * This prevents data loss if the app is killed while in background.
     * 
     * The WAL (Write-Ahead Log) is an optimization that speeds up writes by deferring
     * them to a separate log file. However, if the app is force-killed before the WAL
     * is checkpointed, that data can be lost. This is why your playlist was empty -
     * all the data was in the -wal file but wasn't persisted to the main database.
     */
    fun checkpointDatabase() {
        try {
            // Use a background thread for the checkpoint
            Thread {
                try {
                    val db = database.openHelper.writableDatabase
                    // PRAGMA wal_checkpoint(TRUNCATE) - writes WAL to database and truncates WAL file
                    val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE)")
                    if (cursor.moveToFirst()) {
                        val busy = cursor.getInt(0)
                        val logPages = cursor.getInt(1)
                        val checkpointedPages = cursor.getInt(2)
                        Log.i(TAG, "WAL checkpoint: busy=$busy, log=$logPages, checkpointed=$checkpointedPages")
                    }
                    cursor.close()
                    Log.i(TAG, "Database checkpoint completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during database checkpoint: ${e.message}", e)
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start checkpoint thread: ${e.message}", e)
        }
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Also checkpoint when system asks to trim memory (sign of potential kill)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            Log.w(TAG, "Trim memory level $level - checkpointing database...")
            checkpointDatabase()
        }
    }
}
