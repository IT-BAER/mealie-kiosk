package com.baer.mealiekiosk

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

const val MODEL_DIR = "model"
private const val SAMPLE_RATE = 16000f

/**
 * Offline German voice commands (Vosk, fixed phrase list). Runs only between [start] and [stop].
 * The model is not shipped in the APK: push it to getExternalFilesDir()/model, see README.
 */
class VoiceController(
    private val context: Context,
    private val parser: CommandParser,
    private val onCommand: (Command, String) -> Unit,
) {
    private var model: Model? = null
    private var speech: SpeechService? = null

    fun start() {
        if (speech != null) return
        val dir = File(context.getExternalFilesDir(null), MODEL_DIR)
        if (!dir.isDirectory) {
            Log.e(TAG, "voice model missing at $dir")
            return
        }
        runCatching {
            val m = model ?: Model(dir.absolutePath).also { model = it }
            val recognizer = Recognizer(m, SAMPLE_RATE, parser.grammar()).apply { setWords(true) }
            speech = SpeechService(recognizer, SAMPLE_RATE).also { it.startListening(Listener()) }
            Log.i(TAG, "voice start")
        }.onFailure { Log.e(TAG, "voice start failed", it) }
    }

    fun stop() {
        speech?.let {
            it.stop()
            it.shutdown()
            Log.i(TAG, "voice stop")
        }
        speech = null
    }

    fun close() {
        stop()
        model?.close()
        model = null
    }

    private inner class Listener : RecognitionListener {
        override fun onResult(hypothesis: String) = handle(hypothesis)
        override fun onFinalResult(hypothesis: String) = Unit
        override fun onPartialResult(hypothesis: String) = Unit
        override fun onTimeout() = Unit

        override fun onError(e: Exception) {
            Log.e(TAG, "voice error", e)
        }

        private fun handle(hypothesis: String) {
            val json = JSONObject(hypothesis)
            val text = json.optString("text")
            if (text.isBlank()) return
            val result = json.optJSONArray("result")
            val confidence = (0 until (result?.length() ?: 0))
                .map { result!!.getJSONObject(it).optDouble("conf", 0.0).toFloat() }
                .minOrNull() ?: 0f
            val command = parser.parse(text, confidence)
            Log.i(TAG, "heard \"$text\" conf=$confidence command=$command")
            if (command != null) onCommand(command, text)
        }
    }
}
