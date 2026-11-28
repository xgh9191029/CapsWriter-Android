package com.capswriter.offline.audio

import android.content.Context
import com.capswriter.offline.util.AppLog
import java.util.ArrayDeque

/**
 * Keeps one AudioRecord open while the keyboard is visible and stores the last
 * few 100 ms chunks locally. When a VoiceSession attaches, those chunks are
 * returned as pre-roll, so the first syllable is not lost and AudioRecord does
 * not have to be created/started on the press.
 *
 * The microphone indicator is visible while prewarming; the buffered audio is
 * not uploaded until the user actually starts a recording.
 */
class AudioPrewarmer(private val context: Context) {

    interface Listener {
        fun onChunk(bytes: ByteArray)
        fun onError(message: String)
    }

    private val lock = Any()
    private var capture: AudioCapture? = null
    private var listener: Listener? = null
    private val preRoll = ArrayDeque<ByteArray>()

    fun isRunning(): Boolean = synchronized(lock) { capture != null }

    fun start(): Boolean = synchronized(lock) {
        if (capture != null) return true

        val created = AudioCapture(
            context = context,
            onChunk = { bytes ->
                val sink = synchronized(lock) { listener }
                if (sink != null) {
                    sink.onChunk(bytes)
                } else {
                    synchronized(lock) {
                        if (capture != null) {
                            preRoll.addLast(bytes)
                            while (preRoll.size > MAX_PRE_ROLL_CHUNKS) {
                                preRoll.removeFirst()
                            }
                        }
                    }
                }
            },
            onError = { message ->
                val sink = synchronized(lock) { listener }
                if (sink != null) {
                    sink.onError(message)
                } else {
                    AppLog.add(context, "Microphone prewarm error: $message")
                }
                stop()
            },
        )

        if (!created.start()) {
            created.stop()
            return false
        }
        capture = created
        true
    }

    /**
     * Attach a recording session. Returns the buffered pre-roll, or null when the
     * prewarmer is not running (the caller should fall back to a normal capture).
     */
    fun attach(sink: Listener): List<ByteArray>? = synchronized(lock) {
        if (capture == null) return null
        listener = sink
        val result = preRoll.toList()
        preRoll.clear()
        result
    }

    fun detach(sink: Listener) {
        synchronized(lock) {
            if (listener === sink) listener = null
        }
    }

    fun stop() {
        val current = synchronized(lock) {
            listener = null
            val running = capture
            capture = null
            preRoll.clear()
            running
        }
        current?.stop()
    }

    private companion object {
        /** 5 x 100 ms = 500 ms of pre-roll. */
        const val MAX_PRE_ROLL_CHUNKS = 5
    }
}
