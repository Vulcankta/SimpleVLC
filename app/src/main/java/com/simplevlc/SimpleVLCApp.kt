package com.simplevlc

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import android.widget.Toast
import org.videolan.libvlc.LibVLC

class SimpleVLCApp : Application() {
    
    var libVLC: LibVLC? = null
        private set
    
    private lateinit var themeManager: ThemeManager

    fun reinitializeLibVLC(): LibVLC? {
        if (libVLC == null) {
            libVLC = initializeLibVLC()
            Log.d("SimpleVLCApp", "Attempted to reinitialize LibVLC")
        }
        return libVLC
    }
    
    private fun initializeLibVLC(): LibVLC? {
        return try {
            val options = listOf(
                "--avcodec-hw=any",
                "--network-caching=1000",
                "--file-caching=1000",
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--verbose=2"
            )
            
            val instance = LibVLC(this, options)
            Log.d("SimpleVLCApp", "LibVLC initialized successfully with options: $options")
            instance
        } catch (e: Exception) {
            Log.e("SimpleVLCApp", "LibVLC initialization failed with options, trying without", e)
            
            try {
                val instance = LibVLC(this)
                Log.d("SimpleVLCApp", "LibVLC initialized without options")
                return instance
            } catch (e2: Exception) {
                Log.e("SimpleVLCApp", "LibVLC initialization completely failed", e2)
                Toast.makeText(this, "Failed to initialize video player", Toast.LENGTH_LONG).show()
                null
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Apply saved theme
        themeManager = ThemeManager(this)
        themeManager.applyTheme()
        
        libVLC = initializeLibVLC()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        libVLC?.release()
        libVLC = null
        Log.d("SimpleVLCApp", "Released LibVLC due to low memory")
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Release on severe memory pressure
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            libVLC?.release()
            libVLC = null
            Log.d("SimpleVLCApp", "Released LibVLC due to trim memory level: $level")
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        libVLC?.release()
        libVLC = null
        Log.d("SimpleVLCApp", "Released LibVLC on terminate")
    }
    
    companion object {
        lateinit var instance: SimpleVLCApp
            private set
    }
}
