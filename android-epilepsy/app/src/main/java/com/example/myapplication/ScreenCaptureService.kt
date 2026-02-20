package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.myapplication.analyzer.FrameAnalyzer
import com.example.myapplication.overlay.WarningOverlayManager

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var overlayManager: WarningOverlayManager
    private var handlerThread: android.os.HandlerThread? = null
    private var isCapturing = false
    private var lastOverlayTime = 0L
    
    companion object {
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
        private const val NOTIFICATION_CHANNEL_ID = "ScreenCaptureServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val OVERLAY_COOLDOWN_MS = 1500L // 1.5 second cooldown
        private const val PAUSE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

        @Volatile
        var pausedUntil: Long = 0L
            private set

        // Reference to the service instance for managing floating indicator
        @Volatile
        var instance: ScreenCaptureService? = null
            private set

        fun pauseProtection() {
            pausedUntil = System.currentTimeMillis() + PAUSE_DURATION_MS
            instance?.showPausedIndicator()
        }

        fun resumeProtection() {
            pausedUntil = 0L
            instance?.lastOverlayTime = 0L // Reset cooldown so detection fires immediately
            instance?.hidePausedIndicator()
        }

        fun isPaused(): Boolean {
            return System.currentTimeMillis() < pausedUntil
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        overlayManager = WarningOverlayManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ScreenCaptureService", "Service starting...")
        val notification = createNotification()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d("ScreenCaptureService", "startForeground succeeded")
        } catch (e: Exception) {
            Log.e("ScreenCaptureService", "Failed to start foreground: ${e.message}")
        }

        if (intent != null && intent.action == "START_CAPTURE") {
            // If already capturing, ignore duplicate starts
            if (isCapturing) {
                Log.d("ScreenCaptureService", "Already capturing, ignoring duplicate start")
                return START_NOT_STICKY
            }

            // IMPORTANT: Activity.RESULT_OK == -1 in Android!
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            
            @Suppress("DEPRECATION")
            val resultData: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)

            Log.d("ScreenCaptureService", "resultCode=$resultCode, resultData=${resultData != null}")

            if (resultData == null) {
                Log.e("ScreenCaptureService", "resultData is null, cannot start media projection")
                stopSelf()
                return START_NOT_STICKY
            }

            Log.d("ScreenCaptureService", "Starting capture with MediaProjection")
            startCapture(resultCode, resultData)
        }
        return START_NOT_STICKY
    }

    private fun stopCapture() {
        Log.d("ScreenCaptureService", "Stopping capture...")
        isCapturing = false
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        // Clean up any existing capture first
        if (isCapturing) {
            stopCapture()
        }

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        if (mediaProjection == null) {
            Log.e("ScreenCaptureService", "MediaProjection is null!")
            stopSelf()
            return
        }

        // Android 14 (API 34) requires registering a callback before createVirtualDisplay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d("ScreenCaptureService", "MediaProjection stopped by system")
                    isCapturing = false
                    virtualDisplay?.release()
                    virtualDisplay = null
                    imageReader?.close()
                    imageReader = null
                    handlerThread?.quitSafely()
                    handlerThread = null
                }
            }, Handler(mainLooper))
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)

        val screenDensity = metrics.densityDpi
        // scaling down for performance
        val screenWidth = metrics.widthPixels / 2
        val screenHeight = metrics.heightPixels / 2

        imageReader = try {
            ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        } catch (e: Exception) {
            Log.e("ScreenCaptureService", "Failed to create ImageReader: ${e.message}")
            null
        }
        
        if (imageReader == null) {
            Log.e("ScreenCaptureService", "ImageReader is null, stopping service")
            stopSelf()
            return
        }

        val frameAnalyzer = FrameAnalyzer {
            val now = System.currentTimeMillis()
            if (isPaused()) {
                return@FrameAnalyzer // Skip if paused
            }
            if (now - lastOverlayTime > OVERLAY_COOLDOWN_MS) {
                lastOverlayTime = now
                Log.d("ScreenCaptureService", "Flashing detected! Showing warning overlay.")
                overlayManager.showWarning()
            }
        }
        
        handlerThread = android.os.HandlerThread("ScreenCapture")
        handlerThread?.start()
        val handler = Handler(handlerThread!!.looper)
        
        imageReader?.setOnImageAvailableListener(frameAnalyzer, handler)

        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
        } catch (e: Exception) {
            Log.e("ScreenCaptureService", "createVirtualDisplay failed: ${e.message}")
            stopSelf()
            return
        }
        
        if (virtualDisplay == null) {
            Log.e("ScreenCaptureService", "VirtualDisplay creation failed")
        } else {
            isCapturing = true
            Log.d("ScreenCaptureService", "VirtualDisplay created successfully ($screenWidth x $screenHeight)")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Epilepsy Protection Active")
            .setContentText("Monitoring screen for flashing lights...")
            .setSmallIcon(R.mipmap.ic_launcher) 
            .build()
    }

    // --- Floating Paused Indicator ---
    private var pausedIndicatorView: android.view.View? = null

    fun showPausedIndicator() {
        if (pausedIndicatorView != null) return
        
        Handler(mainLooper).post {
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                params.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                params.y = 120

                val pill = android.widget.TextView(this).apply {
                    text = "  Protection Paused  \u2022  Tap to Resume  "
                    setTextColor(android.graphics.Color.parseColor("#F5EDDA"))
                    textSize = 13f
                    setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
                    setPadding(48, 20, 48, 20)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.parseColor("#5B6F3C"))
                        cornerRadius = 60f
                        setStroke(2, android.graphics.Color.parseColor("#8BA65C"))
                    }
                    setOnClickListener {
                        resumeProtection()
                    }
                }
                
                pausedIndicatorView = pill
                wm.addView(pill, params)
            } catch (e: Exception) {
                Log.e("ScreenCaptureService", "Failed to show paused indicator: ${e.message}")
            }
        }
    }

    fun hidePausedIndicator() {
        Handler(mainLooper).post {
            pausedIndicatorView?.let {
                try {
                    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeView(it)
                } catch (_: Exception) {}
                pausedIndicatorView = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        hidePausedIndicator()
        stopCapture()
        overlayManager.hideWarning()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
