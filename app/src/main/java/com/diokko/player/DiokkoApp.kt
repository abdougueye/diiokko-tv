package com.diokko.player

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DiokkoApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide dependencies here
    }
}
