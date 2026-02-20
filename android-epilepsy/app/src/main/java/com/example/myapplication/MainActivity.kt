package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.overlay.WarningOverlayManager

class MainActivity : AppCompatActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var tvStatusValue: TextView
    private lateinit var tvStatusLabel: TextView
    private lateinit var statusDot: View
    private lateinit var btnResume: Button
    private lateinit var spacer: View
    private lateinit var switchColorblind: Switch
    private lateinit var switchHighContrast: Switch
    private lateinit var rootContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var pauseCheckRunnable: Runnable? = null

    private val screenCaptureIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = "START_CAPTURE"
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data!!.clone() as Intent)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            updateStatus(Status.ACTIVE)
            Toast.makeText(this, "Protection enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        tvStatusValue = findViewById(R.id.tvStatusValue)
        tvStatusLabel = findViewById(R.id.tvStatusLabel)
        statusDot = findViewById(R.id.statusDot)
        btnResume = findViewById(R.id.btnResumeProtection)
        spacer = findViewById(R.id.spacerAfterStop)
        switchColorblind = findViewById(R.id.switchColorblind)
        switchHighContrast = findViewById(R.id.switchHighContrast)
        rootContainer = findViewById(R.id.rootContainer)

        // Load saved accessibility preferences
        switchColorblind.isChecked = AccessibilityPrefs.isColorblindMode(this)
        switchHighContrast.isChecked = AccessibilityPrefs.isHighContrast(this)

        // Apply initial accessibility state
        applyColorblindMode(switchColorblind.isChecked)
        applyHighContrast(switchHighContrast.isChecked)

        // Start Protection
        findViewById<Button>(R.id.btnStartPrediction).setOnClickListener {
            if (checkOverlayPermission()) {
                startScreenCapture()
            } else {
                requestOverlayPermission()
            }
        }

        // Stop Protection
        findViewById<Button>(R.id.btnStopPrediction).setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            updateStatus(Status.INACTIVE)
            Toast.makeText(this, "Protection disabled", Toast.LENGTH_SHORT).show()
        }

        // Resume from Pause
        btnResume.setOnClickListener {
            ScreenCaptureService.resumeProtection()
            btnResume.visibility = View.GONE
            spacer.visibility = View.VISIBLE
            updateStatus(Status.ACTIVE)
            Toast.makeText(this, "Protection resumed", Toast.LENGTH_SHORT).show()
        }

        // Test Overlay
        findViewById<Button>(R.id.btnTestOverlay).setOnClickListener {
            if (checkOverlayPermission()) {
                WarningOverlayManager(this).showWarning()
            } else {
                requestOverlayPermission()
            }
        }

        // Colorblind Mode Toggle
        switchColorblind.setOnCheckedChangeListener { _, isChecked ->
            AccessibilityPrefs.setColorblindMode(this, isChecked)
            applyColorblindMode(isChecked)
            val msg = if (isChecked) "Colorblind-safe mode enabled" else "Colorblind-safe mode disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // High Contrast Toggle
        switchHighContrast.setOnCheckedChangeListener { _, isChecked ->
            AccessibilityPrefs.setHighContrast(this, isChecked)
            applyHighContrast(isChecked)
            val msg = if (isChecked) "High contrast enabled" else "High contrast disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        startPauseCheck()
    }

    private enum class Status { ACTIVE, INACTIVE, PAUSED }

    private fun applyColorblindMode(enabled: Boolean) {
        // Show/hide the text-based status label below the status value
        tvStatusLabel.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun applyHighContrast(enabled: Boolean) {
        if (enabled) {
            rootContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.hc_bg))
            findViewById<TextView>(R.id.tvTitle).setTextColor(ContextCompat.getColor(this, R.color.hc_text))
            findViewById<TextView>(R.id.tvSubtitle).setTextColor(ContextCompat.getColor(this, R.color.hc_accent))
        } else {
            rootContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_dark))
            findViewById<TextView>(R.id.tvTitle).setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            findViewById<TextView>(R.id.tvSubtitle).setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun startPauseCheck() {
        pauseCheckRunnable = object : Runnable {
            override fun run() {
                if (ScreenCaptureService.isPaused()) {
                    btnResume.visibility = View.VISIBLE
                    spacer.visibility = View.GONE
                    updateStatus(Status.PAUSED)
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(pauseCheckRunnable!!)
    }

    private fun updateStatus(status: Status) {
        val isColorblind = AccessibilityPrefs.isColorblindMode(this)
        val isHC = AccessibilityPrefs.isHighContrast(this)

        when (status) {
            Status.ACTIVE -> {
                tvStatusValue.text = "Active"
                tvStatusLabel.text = "[MONITORING]"
                tvStatusValue.contentDescription = "Protection is active and monitoring"
                statusDot.setBackgroundResource(R.drawable.status_dot_active)
                btnResume.visibility = View.GONE
                spacer.visibility = View.VISIBLE

                val color = when {
                    isHC -> R.color.hc_active
                    isColorblind -> R.color.cb_active
                    else -> R.color.status_active
                }
                tvStatusValue.setTextColor(ContextCompat.getColor(this, color))
            }
            Status.INACTIVE -> {
                tvStatusValue.text = "Inactive"
                tvStatusLabel.text = "[STOPPED]"
                tvStatusValue.contentDescription = "Protection is inactive"
                statusDot.setBackgroundResource(R.drawable.status_dot_inactive)
                btnResume.visibility = View.GONE
                spacer.visibility = View.VISIBLE

                val color = when {
                    isHC -> R.color.hc_inactive
                    isColorblind -> R.color.cb_inactive
                    else -> R.color.status_inactive
                }
                tvStatusValue.setTextColor(ContextCompat.getColor(this, color))
            }
            Status.PAUSED -> {
                tvStatusValue.text = "Paused"
                tvStatusLabel.text = "[PAUSED — 5 MIN]"
                tvStatusValue.contentDescription = "Protection is temporarily paused"
                statusDot.setBackgroundResource(R.drawable.status_dot_paused)

                val color = when {
                    isHC -> R.color.hc_accent
                    isColorblind -> R.color.cb_paused
                    else -> R.color.status_paused
                }
                tvStatusValue.setTextColor(ContextCompat.getColor(this, color))
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Please grant display overlay permission", Toast.LENGTH_LONG).show()
        }
    }

    private fun startScreenCapture() {
        screenCaptureIntentLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        super.onDestroy()
        pauseCheckRunnable?.let { handler.removeCallbacks(it) }
    }
}