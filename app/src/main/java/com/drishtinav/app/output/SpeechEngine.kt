package com.drishtinav.app.output

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin wrapper around Android TTS, the primary output channel of the app.
 * The screen is secondary — a blind user hears everything through this.
 */
class SpeechEngine(context: Context) {

    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)

    /** True when the user asked for Hindi; applied as soon as TTS is ready. */
    @Volatile
    private var wantHindi: Boolean = false

    /** True when Hindi (hi-IN) is actually the active TTS language. */
    @Volatile
    var hindiActive: Boolean = false
        private set

    /** True once the TTS engine has finished initializing. */
    val isReady: Boolean get() = ready.get()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                applyLanguage(engine, wantHindi)
                engine.setSpeechRate(speechRate)
                ready.set(true)
            }
        }
    }

    /**
     * Switches the TTS voice between Hindi (hi-IN) and English (en-IN).
     * Returns true when Hindi is actually active afterwards — false when
     * the Hindi voice data is missing or unsupported, in which case English
     * is used as a fallback. Safe to call before TTS is ready; the choice
     * is applied at init.
     */
    fun setSpeechLanguage(hindi: Boolean): Boolean {
        wantHindi = hindi
        val engine = tts
        return if (engine != null && ready.get()) {
            applyLanguage(engine, hindi)
        } else {
            false
        }
    }

    private fun applyLanguage(engine: TextToSpeech, hindi: Boolean): Boolean {
        val locale = if (hindi) Locale("hi", "IN") else Locale("en", "IN")
        val result = engine.setLanguage(locale)
        val ok = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ok && hindi) {
            engine.setLanguage(Locale("en", "IN"))
        }
        hindiActive = hindi && ok
        // Keep the phrase builders in sync — also covers the async init path.
        AppStrings.hindi = hindiActive
        return hindiActive
    }

    /** Speaks immediately, interrupting whatever is currently spoken. */
    fun speakUrgent(text: String) = speak(text, TextToSpeech.QUEUE_FLUSH)

    /** Speaks after the current utterance finishes. */
    fun speakQueued(text: String) = speak(text, TextToSpeech.QUEUE_ADD)

    /**
     * Speech rate multiplier (0.8–1.5). Applied immediately when the
     * engine is ready, otherwise stored for init.
     */
    @Volatile
    var speechRate: Float = 1.1f
        set(value) {
            field = value.coerceIn(0.8f, 1.5f)
            if (ready.get()) tts?.setSpeechRate(field)
        }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready.set(false)
    }

    private fun speak(text: String, queueMode: Int) {
        if (!ready.get()) return
        tts?.speak(text, queueMode, null, "drishtinav-${UUID.randomUUID()}")
    }
}
