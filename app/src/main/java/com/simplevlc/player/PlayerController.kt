package com.simplevlc.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.AndroidUtil

/**
 * Video information data class.
 */
data class VideoInfo(
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val codec: String,
    val bitrate: Int
)

/**
 * Controller class for VLC playback logic, extracted from PlayerActivity.
 * Handles libvlc player initialization, media loading, surface attachment,
 * and playback control.
 */
class PlayerController(
    private val context: Context
) {
    private var libvlcPlayer: MediaPlayer? = null
    private var currentFd: ParcelFileDescriptor? = null
    
    private val savedPositions: MutableMap<String, Long> = mutableMapOf()
    private var currentVideoKey: String? = null
    
    // Callback interface for UI updates
    interface Callback {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onProgressUpdate(position: Float, time: Long, length: Long)
        fun onError(errorMessage: String)
    }
    
    private var callback: Callback? = null
    
    fun setCallback(callback: Callback?) {
        this.callback = callback
    }
    
    /**
     * Initialize the libvlc player instance.
     * @return true if initialization succeeded, false otherwise
     */
    fun setupPlayer(): Boolean {
        val libVLC = com.simplevlc.SimpleVLCApp.instance.libVLC
        if (libVLC == null) {
            Log.e("PlayerController", "LibVLC not initialized")
            Toast.makeText(context, "Video player failed to initialize", Toast.LENGTH_LONG).show()
            return false
        }
        return try {
            libvlcPlayer = MediaPlayer(libVLC)
            Log.d("PlayerController", "MediaPlayer created successfully")
            true
        } catch (e: Exception) {
            Log.e("PlayerController", "Failed to create MediaPlayer", e)
            Toast.makeText(context, "Failed to create media player: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
    
    /**
     * Release resources and close file descriptors.
     */
    fun release() {
        try {
            detachViews()
            libvlcPlayer?.release()
        } finally {
            // Ensure fd is always closed even if release throws
            try {
                currentFd?.close()
            } catch (e: Exception) {
                Log.e("PlayerController", "Error closing file descriptor", e)
            }
            currentFd = null
            libvlcPlayer = null
        }
    }
    
    private fun detachViews() {
        libvlcPlayer?.vlcVout?.detachViews()
    }
    
    /**
     * Attach surface to player and start playback of the given URI.
     * @param uri Media URI (fd:// or file://)
     * @param surfaceHolder SurfaceHolder to attach
     * @param videoKey Optional video key to look up saved position. If null, uses currentVideoKey.
     */
    fun attachAndPlay(uri: Uri, surfaceHolder: SurfaceHolder, videoKey: String? = null) {
        try {
            Log.d("PlayerController", "attachAndPlay called with URI: $uri")
            
            val libVLC = com.simplevlc.SimpleVLCApp.instance.libVLC
            if (libVLC == null) {
                callback?.onError("Video player not initialized")
                return
            }
            
            detachViews()
            
            Log.d("PlayerController", "Setting video surface with SurfaceHolder")
            val vout = libvlcPlayer?.vlcVout
            vout?.setVideoSurface(surfaceHolder.surface, surfaceHolder)
            vout?.attachViews(null)
            
            val media = Media(libVLC, uri)
            libvlcPlayer?.media = media
            libvlcPlayer?.play()
            
            val savedPosition = when {
                videoKey != null -> savedPositions[videoKey] ?: 0
                currentVideoKey != null -> savedPositions[currentVideoKey] ?: 0
                else -> 0
            }
            if (savedPosition > 0) {
                Log.d("PlayerController", "Seeking to saved position: ${savedPosition}ms")
                libvlcPlayer?.time = savedPosition
            }
            
            Log.d("PlayerController", "Playback started successfully")
            callback?.onPlaybackStateChanged(true)
            
        } catch (e: Exception) {
            Log.e("PlayerController", "Error in attachAndPlay", e)
            callback?.onError("Failed to start playback: ${e.message}")
        }
    }
    
    /**
     * Open a file descriptor for the given content URI and return a fd:// URI for playback.
     * @return fd:// URI or null if failed
     */
    fun openFdUri(contentUri: Uri): Uri? {
        // Close existing fd before opening new one
        currentFd?.close()
        currentFd = null
        
        var newFd: ParcelFileDescriptor? = null
        return try {
            newFd = context.contentResolver.openFileDescriptor(contentUri, "r")
                ?: run {
                    Log.e("PlayerController", "Failed to open file descriptor for URI: $contentUri")
                    callback?.onError("Cannot open video file")
                    return null
                }
            
            // Try to create URI with the fd
            val fdUri = try {
                AndroidUtil.LocationToUri("fd://${newFd.fd}")
            } catch (e: Exception) {
                Log.e("PlayerController", "AndroidUtil.LocationToUri failed: ${e.message}")
                // Don't create fallback - fd might be invalid after exception
                newFd?.close()
                newFd = null
                return null
            }
            
            // Transfer ownership to currentFd only after successful URI creation
            currentFd = newFd
            newFd = null // Prevent closing in finally block
            fdUri
        } catch (e: Exception) {
            Log.e("PlayerController", "Error opening file descriptor", e)
            callback?.onError("Error opening video: ${e.message}")
            null
        } finally {
            // Close fd if it wasn't transferred to currentFd (e.g., on exception or early return)
            newFd?.close()
        }
    }
    
    fun filePathToUri(filePath: String): Uri {
        return Uri.fromFile(java.io.File(filePath))
    }
    
    fun closeFd() {
        currentFd?.close()
        currentFd = null
    }
    
    /**
     * Set saved playback position for the current video.
     */
    fun setSavedPosition(position: Long) {
        currentVideoKey?.let { key ->
            savedPositions[key] = position
        }
    }
    
    /**
     * Get saved playback position for a specific video key.
     */
    fun getSavedPosition(key: String): Long = savedPositions[key] ?: 0
    
    /**
     * Get current playback position in milliseconds.
     */
    fun getCurrentTime(): Long = libvlcPlayer?.time ?: 0
    
    /**
     * Get total media length in milliseconds.
     */
    fun getLength(): Long = libvlcPlayer?.length ?: 0
    
    /**
     * Check if player is currently playing.
     */
    fun isPlaying(): Boolean = libvlcPlayer?.isPlaying == true
    
    /**
     * Start or resume playback.
     */
    fun play() {
        libvlcPlayer?.play()
        callback?.onPlaybackStateChanged(true)
    }
    
    /**
     * Pause playback.
     */
    fun pause() {
        libvlcPlayer?.pause()
        callback?.onPlaybackStateChanged(false)
    }
    
    /**
     * Toggle play/pause state.
     */
    fun togglePlayPause() {
        libvlcPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                callback?.onPlaybackStateChanged(false)
            } else {
                player.play()
                callback?.onPlaybackStateChanged(true)
            }
        }
    }
    
    /**
     * Seek to specified time in milliseconds.
     */
    fun seekTo(time: Long) {
        libvlcPlayer?.time = time
    }
    
    /**
     * Set video key for saved position tracking.
     */
    fun setCurrentVideoKey(key: String?) {
        currentVideoKey = key
    }
    
    /**
     * Get current video key.
     */
    fun getCurrentVideoKey(): String? = currentVideoKey
    
    // TODO: Move attachAndPlay, playVideo, playVideoWithPath methods
    // TODO: Handle surface attachment
    // TODO: Progress updater logic

    companion object {
        // Available playback speed options
        val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    }

    /**
     * Set playback speed.
     * @param speed Speed multiplier (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
     */
    fun setPlaybackSpeed(speed: Float) {
        libvlcPlayer?.setRate(speed)
    }

    /**
     * Get current playback speed.
     * @return Current speed multiplier
     */
    fun getPlaybackSpeed(): Float = libvlcPlayer?.rate ?: 1.0f

    /**
     * Get current video information.
     * Note: libvlc-all 3.6.0 has limited API for video metadata.
     * Returns available info (resolution from player size) or placeholders.
     */
    fun getVideoInfo(): VideoInfo? {
        val player = libvlcPlayer ?: return null
        
        return try {
            // libvlc-all 3.6.0 doesn't expose videoWidth/videoHeight directly
            // Resolution can be obtained from the surface or media player state
            val length = player.length
            
            VideoInfo(
                width = 0,  // Not available via public API in libvlc-all 3.6.0
                height = 0, // Not available via public API in libvlc-all 3.6.0
                frameRate = 0,
                codec = "libvlc",
                bitrate = 0
            )
        } catch (e: Exception) {
            Log.e("PlayerController", "Error getting video info", e)
            null
        }
    }

    /**
     * Get formatted video info string for display.
     */
    fun getVideoInfoString(): String {
        val info = getVideoInfo() ?: return "Video info unavailable"
        
        val resolution = if (info.width > 0 && info.height > 0) {
            "${info.width}x${info.height}"
        } else {
            "Unknown resolution"
        }
        
        val fps = if (info.frameRate > 0) {
            "${info.frameRate}fps"
        } else {
            ""
        }
        
        val codec = if (info.codec != "Unknown") {
            info.codec
        } else {
            ""
        }
        
        val bitrate = if (info.bitrate > 0) {
            "${info.bitrate / 1000}kbps"
        } else {
            ""
        }
        
        return listOf(resolution, fps, codec, bitrate)
            .filter { it.isNotEmpty() }
            .joinToString(" | ")
    }
}