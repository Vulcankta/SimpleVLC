package com.simplevlc.player

import android.os.Handler
import android.widget.SeekBar
import android.widget.TextView
import com.simplevlc.utils.TimeUtils

/**
 * Handles periodic progress updates for video playback.
 * Updates seekBar progress, current time, total time, and triggers UI updates.
 */
class ProgressUpdater(
    private val handler: Handler,
    private val seekBar: SeekBar,
    private val currentTimeTextView: TextView,
    private val totalTimeTextView: TextView,
    private val playerController: PlayerController,
    private val isUserSeekingSupplier: () -> Boolean,
    private val updatePlayPauseButton: () -> Unit
) {
    private var progressUpdaterRunnable: Runnable? = null
    private var isRunning = false

    /**
     * Starts periodic progress updates with a 500ms interval.
     * If already running, stops previous updater first.
     */
    fun start() {
        stop()
        isRunning = true
        
        progressUpdaterRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) {
                    return
                }
                
                // Skip updates while user is seeking
                if (!isUserSeekingSupplier()) {
                    try {
                        val length = playerController.getLength()
                        if (length > 0) {
                            val time = playerController.getCurrentTime()
                            val position = time.toFloat() / length
                            seekBar.progress = (position * 100).toInt()
                            currentTimeTextView.text = TimeUtils.formatTime(time)
                            totalTimeTextView.text = TimeUtils.formatTime(length)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                updatePlayPauseButton()
                if (isRunning) {
                    handler.postDelayed(this, 500)
                }
            }
        }
        progressUpdaterRunnable?.let { handler.postDelayed(it, 500) }
    }

    /**
     * Stops progress updates and removes callbacks from the handler.
     */
    fun stop() {
        isRunning = false
        progressUpdaterRunnable?.let { handler.removeCallbacks(it) }
        progressUpdaterRunnable = null
    }

    /**
     * Releases resources and ensures no memory leaks.
     * Should be called when the activity is destroyed.
     */
    fun release() {
        stop()
    }
}