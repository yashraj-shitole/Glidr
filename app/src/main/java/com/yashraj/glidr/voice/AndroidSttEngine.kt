package com.yashraj.glidr.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** [SttEngine] on the platform [SpeechRecognizer]. Must be driven from the main thread. */
class AndroidSttEngine(private val context: Context) : SttEngine {
    private val _state = MutableStateFlow(
        if (SpeechRecognizer.isRecognitionAvailable(context)) SttState.Idle else SttState.Unavailable,
    )
    override val state: StateFlow<SttState> = _state.asStateFlow()
    private val _text = MutableStateFlow("")
    override val text: StateFlow<String> = _text.asStateFlow()
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()
    override val language: String = Locale.getDefault().toLanguageTag()

    private var recognizer: SpeechRecognizer? = null
    private var committed = ""
    private var wantListening = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = SttState.Listening
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _level.value = 0f
        }

        override fun onError(error: Int) {
            _level.value = 0f
            // No match / timeout just means silence: keep listening while the user wants dictation.
            val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (wantListening && quiet) {
                listen()
            } else {
                wantListening = false
                _state.value = SttState.Error
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.firstResult() ?: return
            _text.value = join(committed, partial)
        }

        override fun onResults(results: Bundle?) {
            val final = results?.firstResult()
            if (final != null) {
                committed = join(committed, final)
                _text.value = committed
            }
            if (wantListening) listen() else _state.value = SttState.Idle
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun Bundle.firstResult(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun join(a: String, b: String) = if (a.isEmpty()) b else "$a $b"

    override fun start() {
        if (_state.value == SttState.Unavailable) return
        wantListening = true
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
        }
        listen()
    }

    private fun listen() {
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        _state.value = SttState.Listening
        r.startListening(intent)
    }

    override fun stop() {
        wantListening = false
        recognizer?.stopListening()
        _level.value = 0f
        if (_state.value == SttState.Listening) _state.value = SttState.Idle
    }

    override fun clear() {
        committed = ""
        _text.value = ""
    }

    override fun release() {
        wantListening = false
        recognizer?.destroy()
        recognizer = null
        _level.value = 0f
        if (_state.value != SttState.Unavailable) _state.value = SttState.Idle
    }
}
