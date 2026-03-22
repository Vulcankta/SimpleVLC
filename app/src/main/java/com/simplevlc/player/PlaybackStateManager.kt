package com.simplevlc.player

import android.content.Context
import android.util.Log
import com.simplevlc.repository.PlaybackHistoryRepository

/**
 * Manages playback state including saved positions and current video tracking.
 * Handles persistence via PlaybackHistoryRepository and updates PlayerController
 * when state changes.
 */
class PlaybackStateManager(
    private val context: Context,
    private val playerController: PlayerController
) {
    private var savedPosition: Long = 0
    private var currentVideoKey: String? = null
    private val historyRepository: PlaybackHistoryRepository by lazy {
        PlaybackHistoryRepository(context)
    }

    /**
     * Set the current video key and load its saved position from storage.
     * Updates PlayerController with the saved position.
     */
    fun setCurrentVideoKey(key: String?) {
        currentVideoKey = key
        playerController.setCurrentVideoKey(key)
        
        val position = if (key != null) historyRepository.getPosition(key) else 0L
        savedPosition = position
        playerController.setSavedPosition(position)
        
        Log.d("PlaybackStateManager", "Set video key: $key, saved position: ${position}ms")
    }

    /**
     * Get the current video key.
     */
    fun getCurrentVideoKey(): String? = currentVideoKey

    /**
     * Get the saved position for the current video key.
     */
    fun getSavedPosition(): Long = savedPosition

    /**
     * Get the saved position for a specific video key (without changing current state).
     */
    fun getSavedPosition(key: String): Long {
        return historyRepository.getPosition(key)
    }

    /**
     * Save the current playback position for the current video key.
     * If no current video key is set, does nothing.
     */
    fun saveCurrentPosition(currentTime: Long) {
        currentVideoKey?.let { key ->
            historyRepository.savePosition(key, currentTime)
            savedPosition = currentTime
            playerController.setSavedPosition(currentTime)
            Log.d("PlaybackStateManager", "Saved position ${currentTime}ms for key: $key")
        }
    }

    /**
     * Save a position for a specific video key (e.g., when navigating away).
     * Updates internal savedPosition if the key matches currentVideoKey.
     */
    fun savePosition(key: String, time: Long) {
        historyRepository.savePosition(key, time)
        if (key == currentVideoKey) {
            savedPosition = time
            playerController.setSavedPosition(time)
        }
        Log.d("PlaybackStateManager", "Saved position ${time}ms for key: $key")
    }

    /**
     * Update saved position without persisting (e.g., when loading saved position).
     * Also updates PlayerController.
     */
    fun setSavedPosition(position: Long) {
        savedPosition = position
        playerController.setSavedPosition(position)
        Log.d("PlaybackStateManager", "Set saved position: ${position}ms")
    }

    /**
     * Clear the saved position for a video key.
     */
    fun clearPosition(key: String) {
        historyRepository.clearPosition(key)
        if (key == currentVideoKey) {
            savedPosition = 0
            playerController.setSavedPosition(0)
        }
    }

    /**
     * Called when playback progresses; can be used for auto‑saving logic if needed.
     */
    fun onPlaybackProgress(currentTime: Long) {
        // Optionally auto‑save periodically; currently a no‑op.
    }
}