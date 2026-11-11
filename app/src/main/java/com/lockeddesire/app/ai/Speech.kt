package com.lockeddesire.app.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Text-to-Speech für die KI-Persona. */
class Speech(context: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) try { tts?.language = Locale.GERMAN } catch (_: Exception) { }
        }
    }

    fun speak(text: String, pitch: Float = 1f, rate: Float = 1f) {
        if (!ready) return
        try {
            tts?.setPitch(pitch.coerceIn(0.5f, 2f))
            tts?.setSpeechRate(rate.coerceIn(0.5f, 2f))
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ld")
        } catch (_: Exception) { }
    }

    fun stop() { try { tts?.stop() } catch (_: Exception) { } }
    fun shutdown() { try { tts?.shutdown() } catch (_: Exception) { } }
}
