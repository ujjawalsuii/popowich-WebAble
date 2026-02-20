package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages accessibility preferences using SharedPreferences.
 * Settings persist across app restarts.
 */
object AccessibilityPrefs {
    private const val PREFS_NAME = "epiguard_accessibility"
    private const val KEY_COLORBLIND_MODE = "colorblind_mode"
    private const val KEY_HIGH_CONTRAST = "high_contrast"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isColorblindMode(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_COLORBLIND_MODE, false)
    }

    fun setColorblindMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COLORBLIND_MODE, enabled).apply()
    }

    fun isHighContrast(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_HIGH_CONTRAST, false)
    }

    fun setHighContrast(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
    }
}
