package com.yashraj.glidr.voice

import kotlinx.coroutines.flow.StateFlow

enum class SttState { Idle, Listening, Unavailable, Error }

/** Speech to text. The Android recognizer comes first; an on-device Whisper engine can implement this later. */
interface SttEngine {
    val state: StateFlow<SttState>

    /** Everything recognised so far in this dictation, including the live partial phrase. */
    val text: StateFlow<String>

    /** Microphone level, 0..1, for the meter. */
    val level: StateFlow<Float>

    /** Locale tag in use, for display. */
    val language: String

    fun start()
    fun stop()
    fun clear()
    fun release()
}
