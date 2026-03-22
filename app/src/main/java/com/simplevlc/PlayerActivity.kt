package com.simplevlc

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.simplevlc.model.Video
import com.simplevlc.repository.VideoRepository
import com.simplevlc.repository.BookmarkRepository
import com.simplevlc.databinding.ActivityPlayerBinding
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import com.simplevlc.player.PlayerController
import com.simplevlc.player.PlaybackStateManager
import com.simplevlc.player.ProgressUpdater
import android.media.AudioManager
import android.content.Context
import android.widget.PopupMenu
import com.simplevlc.ui.quicklist.QuickListFragment
import com.simplevlc.service.SleepTimerManager
import com.simplevlc.service.PlaybackService
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.IBinder
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import kotlin.math.abs

class PlayerActivity : AppCompatActivity(),
                       PlayerController.Callback,
                       QuickListFragment.OnVideoSelectedListener {
    
    private lateinit var playerController: PlayerController
    private lateinit var playbackStateManager: PlaybackStateManager
    private var videoRepository: VideoRepository? = null
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var binding: ActivityPlayerBinding
    private var playbackService: PlaybackService? = null
    private var serviceBound = false
    private var currentVideoTitle: String = "Unknown"
    
    private var videoList: List<Video> = emptyList()
    private var currentPosition: Int = 0
    
    private lateinit var surfaceView: SurfaceView
    private lateinit var seekBar: SeekBar
    private lateinit var textViewCurrentTime: TextView
    private lateinit var textViewTotalTime: TextView
    private lateinit var buttonPlayPause: ImageButton
    private lateinit var buttonPrevious: ImageButton
    private lateinit var buttonNext: ImageButton
    private lateinit var buttonOpenPanel: ImageButton
    private lateinit var buttonSpeed: ImageButton
    private lateinit var buttonSnapshot: ImageButton
    private lateinit var buttonInfo: ImageButton
    private lateinit var textViewDebug: TextView
    private lateinit var slidingPaneLayout: SlidingPaneLayout
    private lateinit var quickListFragment: QuickListFragment
    
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private lateinit var progressUpdater: ProgressUpdater
    private var pendingFdUri: Uri? = null
    
    private lateinit var gestureDetector: GestureDetector
    private lateinit var doubleTapGestureDetector: GestureDetector
    private var audioManager: AudioManager? = null
    private var screenBrightness: Float = 0.5f
    private var currentVolume: Int = 0
    private var maxVolume: Int = 0
    private var isBrightnessControl: Boolean = false
    private var gestureStartX: Float = 0f
    private var gestureStartY: Float = 0f
    private var isGestureActive: Boolean = false
    private val touchSlop: Int by lazy { android.view.ViewConfiguration.get(this).scaledTouchSlop }
    
    // Gesture tracking
    private var lastTouchY: Float = 0f
    private val gestureSensitivity: Float = 0.002f

    // PiP mode
    private var pipParams: PictureInPictureParams? = null
    private var isInPipMode = false

    // Rotation lock
    private var isRotationLocked = false
    private lateinit var buttonLock: ImageButton
    private lateinit var buttonSleepTimer: ImageButton
    private lateinit var buttonBookmark: ImageButton
    private lateinit var sleepTimerManager: SleepTimerManager
    private var sleepTimerMinutes = 0

    
    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (!::playerController.isInitialized || !::progressUpdater.isInitialized) {
                // Don't clear pendingFdUri, wait for next surfaceCreated
                return
            }

            pendingFdUri?.let { uri ->
                playerController.attachAndPlay(uri, holder, playbackStateManager.getCurrentVideoKey())
                progressUpdater.start()
                pendingFdUri = null
            }
        }
        
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {

        }
        
        override fun surfaceDestroyed(holder: SurfaceHolder) {

            if (::playerController.isInitialized) {
                playerController.pause()
            }
        }
    }
    
    private fun hasVideoPermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return androidx.core.content.ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    private val gestureListener = object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: android.view.MotionEvent): Boolean {
            gestureStartX = e.x
            gestureStartY = e.y
            lastTouchY = e.y
            isGestureActive = false
            // Determine which side of screen was touched
            val screenWidth = surfaceView.width
            isBrightnessControl = gestureStartX < screenWidth / 2
            return true
        }
        
        override fun onScroll(
            e1: android.view.MotionEvent?,
            e2: android.view.MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {

            // SlidingPaneLayout handles panel edge swipe automatically
            // We only handle brightness/volume gestures here
            
            if (abs(distanceY) > abs(distanceX)) {
                if (!isGestureActive) {
                    // Check if vertical movement exceeds touch slop
                    val deltaY = e2.y - gestureStartY
                    if (abs(deltaY) > touchSlop) {
                        isGestureActive = true
                    } else {
                        return false
                    }
                }
                // Calculate delta from last position
                // Upward swipe: lastTouchY > e2.y → deltaY > 0 → increase
                // Downward swipe: lastTouchY < e2.y → deltaY < 0 → decrease
                val deltaY = lastTouchY - e2.y
                lastTouchY = e2.y
                
                // Apply sensitivity (normalized, no screen height division)
                val normalizedDelta = deltaY * gestureSensitivity
                
                if (isBrightnessControl) {
                    adjustBrightness(normalizedDelta)
                } else {
                    adjustVolume(normalizedDelta)
                }
                return true
            }
            return false
        }
        
    }

    private val doubleTapListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            handleDoubleTap(e.x)
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            return true  // Required for double-tap detection
        }
    }

    private fun handleDoubleTap(x: Float) {
        val screenWidth = surfaceView.width
        val seekAmount = 10_000L // 10 seconds

        when {
            x < screenWidth / 3 -> {
                // Left third - seek backward
                val newTime = (playerController.getCurrentTime() - seekAmount).coerceAtLeast(0)
                playerController.seekTo(newTime)
                showSeekFeedback("-10s")
            }
            x > screenWidth * 2 / 3 -> {
                // Right third - seek forward
                val newTime = (playerController.getCurrentTime() + seekAmount).coerceIn(0, playerController.getLength())
                playerController.seekTo(newTime)
                showSeekFeedback("+10s")
            }
        }
    }

    private fun showSeekFeedback(text: String) {
        binding.textViewSeekFeedback.apply {
            visibility = TextView.VISIBLE
            setText(text)
            alpha = 1f
            animate()
                .alpha(0f)
                .setDuration(800)
                .setStartDelay(500)
                .start()
        }
    }
    
    companion object {
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_POSITION = "extra_video_position"
        const val ACTION_PLAY = "com.simplevlc.ACTION_PLAY"
        const val ACTION_PAUSE = "com.simplevlc.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.simplevlc.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.simplevlc.ACTION_NEXT"
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.LocalBinder
            playbackService = binder.getService()
            serviceBound = true
            
            // Set up callbacks
            playbackService?.onSkipToNext = { playNext() }
            playbackService?.onSkipToPrevious = { playPrevious() }
            playbackService?.onSeekTo = { pos -> playerController.seekTo(pos) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            serviceBound = false
        }
    }
    
    private fun adjustBrightness(delta: Float) {
        // delta positive = swipe up (increase brightness), negative = swipe down
        val current = window.attributes.screenBrightness
        val currentBrightness = if (current > 0) current else screenBrightness
        val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
        val layoutParams = window.attributes
        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams
        screenBrightness = newBrightness
        showFeedback("Brightness: ${(newBrightness * 100).toInt()}%")
    }
    
    private fun adjustVolume(delta: Float) {
        // delta positive = swipe up (increase volume), negative = swipe down
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
        val currentPercent = current.toFloat() / maxVolume.toFloat()
        val newPercent = (currentPercent + delta).coerceIn(0f, 1f)
        val newVolume = (newPercent * maxVolume).toInt().coerceIn(0, maxVolume)
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        val percent = if (maxVolume > 0) (newVolume * 100 / maxVolume) else 0
        showFeedback("Volume: $percent%")
    }
    
    private fun togglePanel() {
        if (slidingPaneLayout.isOpen) {
            slidingPaneLayout.close()
        } else {
            slidingPaneLayout.open()
        }
    }

    private fun toggleRotationLock() {
        isRotationLocked = !isRotationLocked
        if (isRotationLocked) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            buttonLock.setImageResource(R.drawable.ic_lock)
            showFeedback("屏幕已锁定")
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            buttonLock.setImageResource(R.drawable.ic_unlock)
            showFeedback("屏幕已解锁")
        }
    }

    private fun showFeedback(message: String) {
        textViewDebug.text = message
        textViewDebug.visibility = TextView.VISIBLE
        handler.removeCallbacks(hideFeedbackRunnable)
        handler.postDelayed(hideFeedbackRunnable, 1500)
    }
    
    private val hideFeedbackRunnable = Runnable {
        textViewDebug.visibility = View.GONE
    }

    // PiP setup
    private fun setupPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
        }
    }

    // Enter PiP mode
    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pipParams != null) {
            enterPictureInPictureMode(pipParams!!)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initViews()
        setupBackPressHandler()
        setupPip()
        
        playerController = PlayerController(this)
        playerController.setCallback(this)
        playbackStateManager = PlaybackStateManager(this, playerController)
        bookmarkRepository = BookmarkRepository(this)
        
        if (!playerController.setupPlayer()) {
            Log.e("PlayerActivity", "Failed to setup player, finishing activity")
            Toast.makeText(this, "Video player initialization failed", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Initialize progress updater
        progressUpdater = ProgressUpdater(
            handler = handler,
            seekBar = seekBar,
            currentTimeTextView = textViewCurrentTime,
            totalTimeTextView = textViewTotalTime,
            playerController = playerController,
            isUserSeekingSupplier = { isUserSeeking },
            updatePlayPauseButton = { updatePlayPauseButton() }
        )
        
        // Check if we have permission to access videos
        if (!hasVideoPermission()) {
            Toast.makeText(this, "Permission needed to access videos", Toast.LENGTH_LONG).show()
            if (serviceBound) {
                unbindService(serviceConnection)
                serviceBound = false
            }
            finish()
            return
        }
        
        loadVideo()

        // Handle notification action intents
        handleIntentAction(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentAction(intent)
    }

    private fun handleIntentAction(intent: Intent?) {
        intent?.action ?: return
        when (intent.action) {
            ACTION_PLAY -> playerController.play()
            ACTION_PAUSE -> playerController.pause()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_NEXT -> playNext()
        }
    }
    
    private fun initViews() {
        surfaceView = binding.surfaceView
        seekBar = binding.seekBar
        textViewCurrentTime = binding.textViewCurrentTime
        textViewTotalTime = binding.textViewTotalTime
        buttonPlayPause = binding.buttonPlayPause
        buttonPrevious = binding.buttonPrevious
        buttonNext = binding.buttonNext
        buttonOpenPanel = binding.buttonOpenPanel
        buttonSpeed = binding.buttonSpeed
        buttonSnapshot = binding.buttonSnapshot
        buttonLock = binding.buttonLock
        buttonInfo = binding.buttonInfo
        buttonInfo.setOnClickListener { showVideoInfo() }
        buttonSleepTimer = binding.buttonSleepTimer
        buttonSleepTimer.setOnClickListener { showSleepTimerMenu() }
        buttonBookmark = binding.buttonBookmark
        buttonBookmark.setOnClickListener { showBookmarksMenu() }
        textViewDebug = binding.textViewDebug
        slidingPaneLayout = binding.slidingPaneLayout

        // Get reference to quick list fragment
        quickListFragment = supportFragmentManager
            .findFragmentById(R.id.quickListContainer) as QuickListFragment

        // Configure SlidingPaneLayout
        slidingPaneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED

        // Setup panel slide listener
        slidingPaneLayout.addPanelSlideListener(object : SlidingPaneLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) {
                // Optional: could fade main content during slide
            }

            override fun onPanelOpened(panel: View) {
                // Panel is now visible - fragment handles focus
            }

            override fun onPanelClosed(panel: View) {
                // Panel is now hidden - fragment clears search
            }
        })

        buttonPlayPause.setOnClickListener { togglePlayPause() }
        buttonPrevious.setOnClickListener { playPrevious() }
        buttonNext.setOnClickListener { playNext() }
        buttonOpenPanel.setOnClickListener { togglePanel() }
        buttonSpeed.setOnClickListener { showSpeedMenu() }
        buttonLock.setOnClickListener { toggleRotationLock() }
        buttonSnapshot.setOnClickListener { takeSnapshot() }
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = playerController.getLength()
                    val newTime = (progress * duration / 100)
                    playerController.seekTo(newTime)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { isUserSeeking = false }
        })
        
        // Initialize audio manager for volume control
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
        currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        
        // Initialize gesture detector
        gestureDetector = android.view.GestureDetector(this, gestureListener)
        doubleTapGestureDetector = GestureDetector(this, doubleTapListener)
        
        // Set touch listener on surface view for gesture detection
        surfaceView.setOnTouchListener { v, event ->
            // Let double tap detector handle tap events first
            doubleTapGestureDetector.onTouchEvent(event)
            // Let gesture detector handle swipe events
            val handled = gestureDetector.onTouchEvent(event)
            // If gesture detector didn't handle it, pass to default touch handling
            handled || v.performClick()
        }
        
        // Initialize screen brightness
        val currentBrightness = window.attributes.screenBrightness
        screenBrightness = if (currentBrightness > 0) currentBrightness else 0.7f

        // Bind to PlaybackService
        bindService(Intent(this, PlaybackService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        
        // Initialize sleep timer
        sleepTimerManager = SleepTimerManager(handler)
        sleepTimerManager.setOnTimerFinishListener(object : SleepTimerManager.OnTimerFinishListener {
            override fun onTimerFinish() {
                playerController.pause()
                Toast.makeText(this@PlayerActivity, "睡眠定时结束", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun startPlaybackService() {
        val intent = Intent(this, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (slidingPaneLayout.isOpen && slidingPaneLayout.isSlideable) {
                    slidingPaneLayout.close()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
    
    private fun loadVideo() {
        videoRepository = VideoRepository(this)

        videoList = videoRepository?.getVideos() ?: emptyList()

        // Pass videos to fragment
        quickListFragment.setVideos(videoList)

        currentPosition = intent.getIntExtra(EXTRA_VIDEO_POSITION, 0)

        // 优先尝试使用文件路径
        val videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        val videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI)

        // 读取保存的播放位置（优先使用URI作为key，因为saveCurrentPosition使用URI）
        val videoKey = videoUriString ?: videoPath
        playbackStateManager.setCurrentVideoKey(videoKey)
        quickListFragment.setCurrentVideo(videoKey)

        // Update current video title
        if (currentPosition in videoList.indices) {
            currentVideoTitle = videoList[currentPosition].displayName
        } else {
            currentVideoTitle = videoPath?.substringAfterLast('/') ?: "Unknown"
        }

        if (!videoPath.isNullOrEmpty() && File(videoPath).canRead()) {

            surfaceView.holder.removeCallback(surfaceCallback)
            surfaceView.holder.addCallback(surfaceCallback)
            playVideoWithPath(videoPath)
        } else if (!videoUriString.isNullOrEmpty()) {
            // 回退到使用Content URI

            surfaceView.holder.removeCallback(surfaceCallback)
            surfaceView.holder.addCallback(surfaceCallback)
            playVideo(videoUriString)
        }
        
        // Start playback service for foreground notification
        startPlaybackService()
    }
    
    private fun playVideoWithPath(filePath: String) {
        playerController.setPlaybackSpeed(1.0f)
        try {

            
            val fileUri = playerController.filePathToUri(filePath)

            
            if (surfaceView.holder.surface.isValid) {
                Log.d("PlayerActivity", "Surface is already valid, attaching immediately")
                playerController.attachAndPlay(fileUri, surfaceView.holder, playbackStateManager.getCurrentVideoKey())
                progressUpdater.start()
            } else {
                Log.d("PlayerActivity", "Surface not yet valid, storing URI for later attachment")
                pendingFdUri = fileUri
            }
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error in playVideoWithPath", e)
            Toast.makeText(this, "Error playing video: ${e.message}", Toast.LENGTH_LONG).show()
            // 尝试回退到Content URI方式
            val videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI)
            if (!videoUriString.isNullOrEmpty()) {
                playVideo(videoUriString)
            }
        }
    }
    
    private fun playVideo(contentUriString: String) {
        playerController.setPlaybackSpeed(1.0f)
        try {
            Log.d("PlayerActivity", "playVideo called with URI string: $contentUriString")
            val contentUri = Uri.parse(contentUriString)
            
            val fdUri = playerController.openFdUri(contentUri)
            if (fdUri == null) {
                Log.e("PlayerActivity", "Failed to open file descriptor for URI: $contentUri")
                return
            }
            
            Log.d("PlayerActivity", "File descriptor URI created: $fdUri")
            
            if (surfaceView.holder.surface.isValid) {
                Log.d("PlayerActivity", "Surface is already valid, attaching immediately")
                playerController.attachAndPlay(fdUri, surfaceView.holder, playbackStateManager.getCurrentVideoKey())
                progressUpdater.start()
            } else {
                Log.d("PlayerActivity", "Surface not yet valid, storing URI for later attachment")
                pendingFdUri = fdUri
            }
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error in playVideo", e)
            Toast.makeText(this, "Error playing video: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    

    
    private fun togglePlayPause() {
        playerController.togglePlayPause()
        playbackService?.updatePlaybackState(playerController.isPlaying(), currentVideoTitle, playerController.getCurrentTime())
    }
    
    private fun updatePlayPauseButton() {
        val isPlaying = playerController.isPlaying()
        buttonPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateNotification() {
        playbackService?.updatePlaybackState(playerController.isPlaying(), currentVideoTitle, playerController.getCurrentTime())
    }
    
    private fun playPrevious() {
        playerController.setPlaybackSpeed(1.0f)
        if (currentPosition > 0) {
            saveCurrentPosition()
            currentPosition--
            val newVideoUri = videoList[currentPosition].uri.toString()
            currentVideoTitle = videoList[currentPosition].displayName
            playbackStateManager.setCurrentVideoKey(newVideoUri)
            playVideo(newVideoUri)
            playbackService?.updatePlaybackState(playerController.isPlaying(), currentVideoTitle, playerController.getCurrentTime())
        }
    }

    private fun playNext() {
        playerController.setPlaybackSpeed(1.0f)
        if (currentPosition < videoList.size - 1) {
            saveCurrentPosition()
            currentPosition++
            val newVideoUri = videoList[currentPosition].uri.toString()
            currentVideoTitle = videoList[currentPosition].displayName
            playbackStateManager.setCurrentVideoKey(newVideoUri)
            playVideo(newVideoUri)
            playbackService?.updatePlaybackState(playerController.isPlaying(), currentVideoTitle, playerController.getCurrentTime())
        }
    }

    // QuickListFragment.OnVideoSelectedListener implementation
    override fun onVideoSelected(video: Video) {
        val position = videoList.indexOfFirst { it.uri == video.uri }
        if (position >= 0) {
            saveCurrentPosition()
            currentPosition = position
            currentVideoTitle = video.displayName
            playbackStateManager.setCurrentVideoKey(video.uri.toString())
            quickListFragment.setCurrentVideo(video.uri.toString())
            playVideo(video.uri.toString())
            playbackService?.updatePlaybackState(playerController.isPlaying(), currentVideoTitle, playerController.getCurrentTime())
            if (slidingPaneLayout.isOpen) {
                slidingPaneLayout.close()
            }
        }
    }

    
    private fun saveCurrentPosition() {
        val currentTime = playerController.getCurrentTime()
        playbackStateManager.saveCurrentPosition(currentTime)
    }
    
    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        updatePlayPauseButton()
    }
    
    override fun onProgressUpdate(position: Float, time: Long, length: Long) {
        // Sync position to service for notification
        playbackService?.setPosition(time)
        playbackService?.updatePlaybackState(playerController.isPlaying(), currentVideoTitle, time)
    }
    
    override fun onError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    // PiP callback
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        
        if (isInPictureInPictureMode) {
            // Hide controls in PiP mode
            binding.controlsLayout.visibility = View.GONE
            binding.textViewSeekFeedback.visibility = View.GONE
            binding.textViewDebug.visibility = View.GONE
        } else {
            // Restore controls when exiting PiP
            binding.controlsLayout.visibility = View.VISIBLE
        }
    }

    // Allow background audio playback when user presses Home
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerController.isPlaying()) {
            enterPipMode()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Only save position if not in PiP mode - don't pause playback
        // Playback continues in background via PlaybackService
        if (!isInPipMode) {
            saveCurrentPosition()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("PlayerActivity", "onDestroy")
        saveCurrentPosition()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        playerController.setPlaybackSpeed(1.0f)  // Reset speed on exit
        if (::progressUpdater.isInitialized) {
            progressUpdater.stop()
        }
        handler.removeCallbacks(hideFeedbackRunnable)
        // Remove all pending handler messages to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
        surfaceView.holder.removeCallback(surfaceCallback)
        playerController.release()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        sleepTimerManager.cancel()
    }

    private fun showSpeedMenu() {
        val popup = PopupMenu(this, buttonSpeed)
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        speeds.forEachIndexed { index, speed ->
            popup.menu.add(0, index, index, speed)
        }
        popup.setOnMenuItemClickListener { item ->
            val selectedSpeed = PlayerController.SPEED_OPTIONS[item.itemId]
            playerController.setPlaybackSpeed(selectedSpeed)
            showFeedback("Speed: ${selectedSpeed}x")
            true
        }
        popup.show()
    }

    private fun showVideoInfo() {
        val info = playerController.getVideoInfoString()
        val duration = playerController.getLength()
        val durationStr = formatTime(duration)
        
        AlertDialog.Builder(this)
            .setTitle("视频信息")
            .setMessage("时长: $durationStr\n\n$info")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showSleepTimerMenu() {
        val timerOptions = arrayOf("关闭", "15分钟", "30分钟", "45分钟", "60分钟", "90分钟")
        
        val currentSelection = when {
            !sleepTimerManager.isRunning() -> 0
            sleepTimerMinutes == 15 -> 1
            sleepTimerMinutes == 30 -> 2
            sleepTimerMinutes == 45 -> 3
            sleepTimerMinutes == 60 -> 4
            sleepTimerMinutes == 90 -> 5
            else -> 0
        }
        
        AlertDialog.Builder(this)
            .setTitle("睡眠定时器")
            .setSingleChoiceItems(timerOptions, currentSelection) { dialog, which ->
                when (which) {
                    0 -> {
                        sleepTimerManager.cancel()
                        sleepTimerMinutes = 0
                        showFeedback("定时器已关闭")
                    }
                    1 -> {
                        sleepTimerMinutes = 15
                        sleepTimerManager.start(15)
                        showFeedback("定时器: 15分钟")
                    }
                    2 -> {
                        sleepTimerMinutes = 30
                        sleepTimerManager.start(30)
                        showFeedback("定时器: 30分钟")
                    }
                    3 -> {
                        sleepTimerMinutes = 45
                        sleepTimerManager.start(45)
                        showFeedback("定时器: 45分钟")
                    }
                    4 -> {
                        sleepTimerMinutes = 60
                        sleepTimerManager.start(60)
                        showFeedback("定时器: 60分钟")
                    }
                    5 -> {
                        sleepTimerMinutes = 90
                        sleepTimerManager.start(90)
                        showFeedback("定时器: 90分钟")
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddBookmarkDialog() {
        val currentTime = playerController.getCurrentTime()
        
        AlertDialog.Builder(this)
            .setTitle("添加书签")
            .setMessage("在 ${formatTime(currentTime)} 处添加书签")
            .setPositiveButton("添加") { _, _ ->
                val label = "书签 ${getNextBookmarkNumber()}"
                val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return@setPositiveButton
                bookmarkRepository.addBookmark(videoUri, currentTime, label)
                showFeedback("书签已添加")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getNextBookmarkNumber(): Int {
        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return 1
        return bookmarkRepository.getBookmarks(videoUri).size + 1
    }

    private fun showBookmarksMenu() {
        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (videoUri.isNullOrEmpty()) {
            Toast.makeText(this, "无法获取视频信息", Toast.LENGTH_SHORT).show()
            return
        }
        
        val bookmarks = bookmarkRepository.getBookmarks(videoUri)
        
        if (bookmarks.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("书签")
                .setMessage("暂无书签")
                .setPositiveButton("添加当前书签") { _, _ -> showAddBookmarkDialog() }
                .setNegativeButton("关闭", null)
                .show()
            return
        }
        
        val items = bookmarks.map { "${it.label} - ${formatTime(it.position)}" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("书签列表")
            .setItems(items) { _, which ->
                val bookmark = bookmarks[which]
                playerController.seekTo(bookmark.position)
                showFeedback("跳转到 ${formatTime(bookmark.position)}")
            }
            .setPositiveButton("添加当前书签") { _, _ -> showAddBookmarkDialog() }
            .setNeutralButton("删除") { _, _ ->
                showDeleteBookmarkDialog(videoUri, bookmarks)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showDeleteBookmarkDialog(videoUri: String, bookmarks: List<com.simplevlc.repository.BookmarkRepository.Bookmark>) {
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "暂无书签可删除", Toast.LENGTH_SHORT).show()
            return
        }
        
        val items = bookmarks.map { "${it.label} - ${formatTime(it.position)}" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("选择要删除的书签")
            .setItems(items) { _, which ->
                val bookmark = bookmarks[which]
                bookmarkRepository.removeBookmark(videoUri, bookmark.position)
                Toast.makeText(this, "已删除: ${bookmark.label}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    private fun takeSnapshot() {
        try {
            val retriever = MediaMetadataRetriever()
            val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI)
            
            if (videoUri.isNullOrEmpty()) {
                Toast.makeText(this, "截图失败：无法获取视频", Toast.LENGTH_SHORT).show()
                return
            }
            
            retriever.setDataSource(this, Uri.parse(videoUri))
            
            val bitmap = retriever.getFrameAtTime(
                playerController.getCurrentTime() * 1000, // Convert ms to µs
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            
            retriever.release()
            
            if (bitmap == null) {
                Toast.makeText(this, "截图失败：无法获取帧", Toast.LENGTH_SHORT).show()
                return
            }
            
            val cacheDir = externalCacheDir ?: cacheDir
            val fileName = "SimpleVLC_${System.currentTimeMillis()}.jpg"
            val file = File(cacheDir, fileName)
            
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            
            Toast.makeText(this, "截图已保存: ${file.name}", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Screenshot failed", e)
            Toast.makeText(this, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
