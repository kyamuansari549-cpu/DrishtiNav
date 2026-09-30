package com.drishtinav.app.output

import android.os.SystemClock
import com.drishtinav.app.perception.Direction
import com.drishtinav.app.perception.Obstacle
import com.drishtinav.app.perception.Urgency

/**
 * Decides what the user hears, and when.
 *
 * Phase 1 policy:
 * - URGENT (< 1 m, any direction): announced immediately, interrupts speech,
 *   strong haptic. 2.5 s cooldown per object+direction.
 * - NEAR (1–2 m, CENTER only): queued behind current speech, medium haptic.
 *   5 s cooldown per object+direction. Side-zone NEAR objects stay silent —
 *   they are not in the walking path.
 * - FAR: never announced (still visible in logs for tuning).
 *
 * At most one non-urgent utterance starts per 1.2 s so speech never becomes
 * a wall of noise.
 */
class AlertPolicy(
    private val speech: SpeechEngine,
    private val haptics: HapticEngine,
    private val onAnnouncement: (String) -> Unit = {}
) {

    /** When true, NEAR obstacles in left/right zones are announced too. */
    var announceSideNear: Boolean = false

    private val lastSpokenAt = mutableMapOf<String, Long>()
    private var lastAnySpeechAt = 0L

    /** Returns the announced text, or null when nothing was announced. */
    fun evaluate(obstacles: List<Obstacle>): String? {
        val now = SystemClock.elapsedRealtime()

        val candidates = obstacles.filter { obstacle ->
            when (obstacle.urgency) {
                Urgency.URGENT -> true
                Urgency.NEAR ->
                    obstacle.direction == Direction.CENTER || announceSideNear
                Urgency.FAR -> false
            }
        }
        val target = candidates.minByOrNull { it.distanceMeters ?: Float.MAX_VALUE }
            ?: return null

        val key = "${target.label}:${target.direction}"
        val cooldown = if (target.urgency == Urgency.URGENT) {
            URGENT_COOLDOWN_MS
        } else {
            NEAR_COOLDOWN_MS
        }
        if (now - (lastSpokenAt[key] ?: 0L) < cooldown) return null
        if (target.urgency != Urgency.URGENT && now - lastAnySpeechAt < MIN_SPEECH_GAP_MS) {
            return null
        }

        val text = describe(target)
        lastSpokenAt[key] = now
        lastAnySpeechAt = now

        if (target.urgency == Urgency.URGENT) {
            speech.speakUrgent(text)
            haptics.urgent()
        } else {
            speech.speakQueued(text)
            haptics.near()
        }
        onAnnouncement(text)
        return text
    }

    private fun describe(obstacle: Obstacle): String {
        return AppStrings.obstacleAlert(
            labelEn = obstacle.label,
            distanceMeters = obstacle.distanceMeters,
            direction = obstacle.direction,
            urgent = obstacle.urgency == Urgency.URGENT
        )
    }

    companion object {
        private const val URGENT_COOLDOWN_MS = 2_500L
        private const val NEAR_COOLDOWN_MS = 5_000L
        private const val MIN_SPEECH_GAP_MS = 1_200L
    }
}
