package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.overlay.WarningOverlayManager

class MainActivity : AppCompatActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var tvStatusValue: TextView

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
            updateStatus(true)
            Toast.makeText(this, "Protection Started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        tvStatusValue = findViewById(R.id.tvStatusValue)

        findViewById<Button>(R.id.btnStartPrediction).setOnClickListener {
            if (checkOverlayPermission()) {
                startScreenCapture()
            } else {
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.btnStopPrediction).setOnClickListener {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java)
            stopService(serviceIntent)
            updateStatus(false)
            Toast.makeText(this, "Protection Stopped", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnTestOverlay).setOnClickListener {
            if (checkOverlayPermission()) {
                val overlayManager = WarningOverlayManager(this)
                overlayManager.showWarning()
            } else {
                requestOverlayPermission()
            }
        }
    }

    private fun updateStatus(active: Boolean) {
        if (active) {
            tvStatusValue.text = "● Active"
            tvStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_active))
        } else {
            tvStatusValue.text = "● Inactive"
            tvStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
        }
    }

    private fun startScreenCapture() {
        screenCaptureIntentLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}