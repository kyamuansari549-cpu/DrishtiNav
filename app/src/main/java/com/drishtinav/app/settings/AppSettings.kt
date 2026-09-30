package com.drishtinav.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * User-tunable behaviour, persisted in SharedPreferences.
 * Applied live: MainActivity re-reads these in onResume.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** TTS speech rate multiplier. */
    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, 1.1f)
        set(value) = prefs.edit { putFloat(KEY_SPEECH_RATE, value.coerceIn(0.8f, 1.5f)) }

    /** Below this distance (m), an obstacle interrupts speech. */
    var urgentDistanceM: Float
        get() = prefs.getFloat(KEY_URGENT_M, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_URGENT_M, value.coerceIn(0.5f, 2.0f)) }

    /** Below this distance (m), a walking-path obstacle is announced. */
    var nearDistanceM: Float
        get() = prefs.getFloat(KEY_NEAR_M, 2.0f)
        set(value) {
            // Near band must stay wider than the urgent band.
            val clamped = value.coerceIn(1.0f, 4.0f).coerceAtLeast(urgentDistanceM + 0.5f)
            prefs.edit { putFloat(KEY_NEAR_M, clamped) }
        }

    /** Keep the screen on while scanning (prevents mid-walk lock). */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, value) }

    /** Also announce NEAR obstacles in the left/right zones (default: silent). */
    var announceSideNear: Boolean
        get() = prefs.getBoolean(KEY_SIDE_NEAR, false)
        set(value) = prefs.edit { putBoolean(KEY_SIDE_NEAR, value) }

    /**
     * Guidance language: [LANG_EN] for English, [LANG_HI] for Hindi.
     * Hindi needs the hi-IN TTS voice on the device; SpeechEngine falls
     * back to English when it is missing.
     */
    var speechLanguage: String
        get() = prefs.getString(KEY_SPEECH_LANG, LANG_EN) ?: LANG_EN
        set(value) = prefs.edit {
            putString(KEY_SPEECH_LANG, if (value == LANG_HI) LANG_HI else LANG_EN)
        }

    companion object {
        const val LANG_EN = "en"
        const val LANG_HI = "hi"
        private const val PREFS_NAME = "drishtinav_settings"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_URGENT_M = "urgent_distance_m"
        private const val KEY_NEAR_M = "near_distance_m"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SIDE_NEAR = "announce_side_near"
        private const val KEY_SPEECH_LANG = "speech_language"
    }
}
