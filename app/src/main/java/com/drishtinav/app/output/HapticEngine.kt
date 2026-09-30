package com.drishtinav.app.output

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic alerts — the secondary channel. Distinct patterns so the user can
 * tell urgency apart without listening: urgent is a long triple buzz,
 * near is a double tap, tick is UI confirmation.
 */
class HapticEngine(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun urgent() = buzz(longArrayOf(0, 160, 80, 160, 80, 280))

    fun near() = buzz(longArrayOf(0, 120, 100, 120))

    fun tick() = buzz(longArrayOf(0, 40))

    private fun buzz(pattern: LongArray) {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
