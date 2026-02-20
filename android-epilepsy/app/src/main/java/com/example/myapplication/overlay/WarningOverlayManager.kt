package com.example.myapplication.overlay

import android.app.ActivityManager
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class WarningOverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    
    fun showWarning() {
        if (overlayView != null) return
        
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.CENTER

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF5000000.toInt())
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }

        // Warning icon (⚠ symbol using text)
        val warningIcon = TextView(context).apply {
            text = "⚠"
            setTextColor(Color.parseColor("#FFCC00"))
            textSize = 72f
            gravity = Gravity.CENTER
        }
        rootLayout.addView(warningIcon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        // Title
        val title = TextView(context).apply {
            text = "Flashing Lights Detected"
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }
        rootLayout.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // Subtitle
        val subtitle = TextView(context).apply {
            text = "Rapid flashing content has been blocked\nto protect you from potential seizures."
            setTextColor(Color.parseColor("#8B949E"))
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        rootLayout.addView(subtitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(48) })

        // Buttons container
        val buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // 1. Dismiss button (primary action)
        val dismissBtn = createStyledButton(
            text = "🛡️  Dismiss & Resume",
            bgColor = Color.parseColor("#6C63FF"),
            textColor = Color.WHITE
        ) {
            hideWarning()
        }
        buttonsContainer.addView(dismissBtn, createButtonParams())

        // 2. Close App button
        val closeAppBtn = createStyledButton(
            text = "✕  Close Unsafe App",
            bgColor = Color.parseColor("#DA3633"),
            textColor = Color.WHITE
        ) {
            closeCurrentApp()
            hideWarning()
        }
        buttonsContainer.addView(closeAppBtn, createButtonParams())

        // 3. Call for Help button
        val callHelpBtn = createStyledButton(
            text = "📞  Call Emergency Contact",
            bgColor = Color.parseColor("#238636"),
            textColor = Color.WHITE
        ) {
            callForHelp()
            hideWarning()
        }
        buttonsContainer.addView(callHelpBtn, createButtonParams())

        rootLayout.addView(buttonsContainer)

        // Safety tip at bottom
        val safetyTip = TextView(context).apply {
            text = "If you are experiencing symptoms, sit down in a safe place\nand close your eyes until the flashing has stopped."
            setTextColor(Color.parseColor("#6E7681"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(32), dp(16), 0)
        }
        rootLayout.addView(safetyTip)

        overlayView = rootLayout
        windowManager.addView(rootLayout, layoutParams)
    }
    
    private fun createStyledButton(
        text: String,
        bgColor: Int,
        textColor: Int,
        onClick: () -> Unit
    ): Button {
        return Button(context).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            setPadding(dp(24), dp(16), dp(24), dp(16))

            val shape = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(14).toFloat()
            }
            background = shape

            setOnClickListener { onClick() }
        }
    }

    private fun createButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            dp(280),
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
            gravity = Gravity.CENTER
        }
    }

    private fun closeCurrentApp() {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val tasks = activityManager.getRunningTasks(2)
            if (tasks.size > 1) {
                // The top task is our overlay-related, the second is the offending app
                val targetPackage = tasks[1].baseActivity?.packageName
                if (targetPackage != null && targetPackage != context.packageName) {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            } else {
                // Fallback: just go to home screen
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            // Fallback: go home
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    private fun callForHelp() {
        try {
            val callIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:911")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
        } catch (e: Exception) {
            // Couldn't open dialer
        }
    }
    
    fun hideWarning() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View might already be removed
            }
            overlayView = null
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
