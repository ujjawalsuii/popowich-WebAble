package com.example.myapplication.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.myapplication.AccessibilityPrefs
import com.example.myapplication.ScreenCaptureService

class WarningOverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun showWarning() {
        if (overlayView != null) return

        val isHC = AccessibilityPrefs.isHighContrast(context)
        val isCB = AccessibilityPrefs.isColorblindMode(context)

        // Adaptive colors
        val bgColor = if (isHC) Color.BLACK else Color.parseColor("#F2111510")
        val textPrimary = if (isHC) Color.WHITE else Color.parseColor("#F5EDDA")
        val textSecondary = if (isHC) Color.parseColor("#CCCCCC") else Color.parseColor("#A9A28E")
        val textHint = if (isHC) Color.parseColor("#AAAAAA") else Color.parseColor("#7A7564")

        val colDismiss = when {
            isHC -> Color.parseColor("#006600")
            isCB -> Color.parseColor("#648FFF")
            else -> Color.parseColor("#5B6F3C")
        }
        val colPause = when {
            isHC -> Color.YELLOW
            isCB -> Color.parseColor("#FFB000")
            else -> Color.parseColor("#C9A84C")
        }
        val colClose = when {
            isHC -> Color.RED
            isCB -> Color.parseColor("#DC267F")
            else -> Color.parseColor("#BF4040")
        }
        val colHelp = when {
            isHC -> Color.parseColor("#00CC00")
            isCB -> Color.parseColor("#785EF0")
            else -> Color.parseColor("#5B8C3E")
        }
        val btnText = if (isHC) Color.BLACK else textPrimary
        val pauseBtnText = if (isHC) Color.BLACK else Color.parseColor("#1A1F14")

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.CENTER

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bgColor)
            setPadding(dp(36), dp(56), dp(36), dp(56))
        }

        // Warning indicator line
        val indicatorLine = View(context).apply {
            background = GradientDrawable().apply {
                setColor(colClose)
                cornerRadius = dp(2).toFloat()
            }
        }
        root.addView(indicatorLine, LinearLayout.LayoutParams(dp(48), dp(4)).apply {
            gravity = Gravity.CENTER
            bottomMargin = dp(28)
        })

        // Title
        root.addView(TextView(context).apply {
            text = "Flashing Content Detected"
            setTextColor(textPrimary)
            textSize = if (isHC) 28f else 24f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            gravity = Gravity.CENTER
            contentDescription = "Warning: Flashing content has been detected on your screen"
        }, wrapParams(dp(10)))

        // Description
        root.addView(TextView(context).apply {
            text = "Rapid flashing has been detected on screen.\nThe display has been covered for your safety."
            setTextColor(textSecondary)
            textSize = if (isHC) 16f else 14f
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
        }, wrapParams(dp(36)))

        // Colorblind label
        if (isCB) {
            root.addView(TextView(context).apply {
                text = "[WARNING: SEIZURE RISK DETECTED]"
                setTextColor(textHint)
                textSize = 12f
                gravity = Gravity.CENTER
                letterSpacing = 0.08f
            }, wrapParams(dp(16)))
        }

        // Buttons — larger touch targets (minimum 48dp tall per WCAG)
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // Button labels include function description for accessibility
        buttons.addView(
            makeButton("Dismiss", colDismiss, btnText, "Dismiss warning and return to screen") { hideWarning() },
            btnParams()
        )
        buttons.addView(
            makeButton("Pause for 5 Minutes", colPause, pauseBtnText, "Pause flash detection for five minutes") {
                ScreenCaptureService.pauseProtection()
                hideWarning()
            },
            btnParams()
        )
        buttons.addView(
            makeButton("Close Unsafe App", colClose, btnText, "Close the app showing flashing content") {
                goHome()
                hideWarning()
            },
            btnParams()
        )
        buttons.addView(
            makeButton("Call Emergency Services", colHelp, btnText, "Open phone dialer to call emergency services") {
                callEmergency()
                hideWarning()
            },
            btnParams()
        )

        root.addView(buttons)

        // Safety note
        root.addView(TextView(context).apply {
            text = "If you are experiencing symptoms, stay seated and\nclose your eyes until the episode passes."
            setTextColor(textHint)
            textSize = if (isHC) 13f else 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, 0)
        })

        overlayView = root
        windowManager.addView(root, layoutParams)
    }

    private fun makeButton(label: String, bgCol: Int, txtCol: Int, desc: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(txtCol)
            textSize = 15f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            isAllCaps = false
            // Minimum 48dp touch target per WCAG operability guidelines
            minimumHeight = dp(52)
            setPadding(dp(24), dp(16), dp(24), dp(16))
            background = GradientDrawable().apply {
                setColor(bgCol)
                cornerRadius = dp(12).toFloat()
            }
            contentDescription = desc
            setOnClickListener { onClick() }
        }
    }

    private fun wrapParams(bottomMargin: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { this.bottomMargin = bottomMargin }

    private fun btnParams() = LinearLayout.LayoutParams(dp(280), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(10)
        gravity = Gravity.CENTER
    }

    private fun goHome() {
        try {
            context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: Exception) {}
    }

    private fun callEmergency() {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:911")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: Exception) {}
    }

    fun hideWarning() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
    ).toInt()
}
