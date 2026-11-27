package com.capswriter.offline.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.capswriter.offline.R
import com.capswriter.offline.net.CapsWriterSocket
import com.capswriter.offline.util.AppLog
import com.capswriter.offline.util.VoiceState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A single press-to-talk recording. A fresh WebSocket is used for every utterance
 * so a slow/failed recognition can never poison the next one.
 */
class VoiceSession(
    private val context: Context,
    private val serverUrl: String,
    private val hotwordContext: String,
    private val language: String,
    private val saveAudio: Boolean,
    private val minRecordMs: Int,
    private val callback: Callback,
) {

    interface Callback {
        fun onState(state: VoiceState, message: String)
        fun onResult(text: String)
        fun onError(message: String)
        fun onCancelled(message: String)
        fun onFinished()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val taskId = UUID.randomUUID().toString()
    private val startTime = System.currentTimeMillis() / 1000.0
    private val startedElapsed = SystemClock.elapsedRealtime()
    private val ready = CompletableDeferred<Boolean>()
    private val stopped = AtomicBoolean(false)
    private val completed = AtomicBoolean(false)

    @Volatile
    private var socketOpen = false
    private var capture: AudioCapture? = null
    private var socket: CapsWriterSocket? = null
    private var connectTimeout: Job? = null
    private var finalTimeout: Job? = null

    @Volatile
    private var wavWriter: AudioWavWriter? = null

    fun start() {
        AppLog.add(context, "Voice task started ${taskId.take(8)}")
        postState(VoiceState.CONNECTING, context.getString(R.string.state_connecting))

        val connection = CapsWriterSocket(
            url = serverUrl,
            taskId = taskId,
            hotwordContext = hotwordContext,
            language = language,
            listener = object : CapsWriterSocket.Listener {
                override fun onOpen() {
                    socketOpen = true
                    connectTimeout?.cancel()
                    if (stopped.get()) {
                        postState(VoiceState.PROCESSING, context.getString(R.string.state_processing))
                    } else {
                        postState(VoiceState.RECORDING, context.getString(R.string.state_recording))
                    }
                }

                override fun onFinalText(text: String) {
                    complete(text.trim())
                }

                override fun onFailure(message: String) {
                    fail(context.getString(R.string.error_connect_server, message))
                }

                override fun onClosed() {
                    if (!completed.get()) fail(context.getString(R.string.error_server_closed))
                }
            },
        )
        socket = connection
        try {
            connection.connect()
        } catch (error: Exception) {
            fail(context.getString(R.string.error_invalid_server, error.message ?: error.javaClass.simpleName))
            return
        }

        scope.launch {
            if (completed.get()) {
                ready.complete(false)
                return@launch
            }
            if (saveAudio) {
                runCatching {
                    AudioWavWriter(context).also { it.open() }
                }.onSuccess {
                    wavWriter = it
                    AppLog.add(context, "Recording will be saved to ${AudioWavWriter.directory(context).absolutePath}")
                }.onFailure {
                    AppLog.add(context, "Cannot create recording file: ${it.message ?: it.javaClass.simpleName}")
                }
            }
            val audio = AudioCapture(
                context = context,
                onChunk = { bytes ->
                    if (!stopped.get() && !completed.get()) {
                        connection.sendAudio(bytes, isFinal = false, startTime = startTime)
                        runCatching { wavWriter?.writeFloat32Le(bytes) }
                            .onFailure {
                                AppLog.add(context, "Recording write failed; saving disabled: ${it.message.orEmpty()}")
                                wavWriter?.discard()
                                wavWriter = null
                            }
                    }
                },
                onError = { message -> fail(context.getString(R.string.error_mic, message)) },
            )
            val started = audio.start()
            if (started) capture = audio
            ready.complete(started)
            if (started) {
                if (stopped.get() || completed.get()) {
                    audio.stop()
                } else if (!socketOpen) {
                    postState(VoiceState.RECORDING, context.getString(R.string.state_recording))
                }
            }
        }

        connectTimeout = scope.launch {
            delay(7_000)
            if (!socketOpen && !completed.get()) {
                fail(context.getString(R.string.error_connect_timeout))
            }
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        val elapsed = SystemClock.elapsedRealtime() - startedElapsed

        if (elapsed < minRecordMs) {
            cancel(context.getString(R.string.toast_hold_longer))
            return
        }

        AppLog.add(context, "Recording stopped after ${elapsed / 1000.0}s")
        postState(VoiceState.PROCESSING, context.getString(R.string.state_processing))

        scope.launch {
            withTimeoutOrNull(2_500) { ready.await() }
            capture?.stop()
            if (completed.get()) return@launch
            socket?.sendAudio(ByteArray(0), isFinal = true, startTime = startTime)
            if (!completed.get()) {
                finalTimeout = scope.launch {
                    delay(FINAL_TIMEOUT_MS)
                    if (!completed.get()) fail(context.getString(R.string.error_recognition_timeout))
                }
            }
        }
    }

    fun cancel(reason: String) {
        if (!completed.compareAndSet(false, true)) return
        scope.launch {
            discardRecording()
            cleanup()
            main.post {
                callback.onCancelled(reason)
                callback.onFinished()
            }
        }
    }

    private fun complete(text: String) {
        if (!completed.compareAndSet(false, true)) return
        scope.launch {
            cleanup()
            val saved = finishRecording(text)
            main.post {
                saved?.let { AppLog.add(context, "Recording saved: ${it.absolutePath}") }
                if (text.isBlank()) {
                    callback.onError(context.getString(R.string.error_no_speech))
                } else {
                    AppLog.add(context, "Recognition complete: ${text.length} chars")
                    callback.onResult(text)
                }
                callback.onFinished()
            }
        }
    }

    private fun fail(message: String) {
        if (!completed.compareAndSet(false, true)) return
        AppLog.add(context, message)
        scope.launch {
            discardRecording()
            cleanup()
            main.post {
                callback.onState(VoiceState.ERROR, message)
                callback.onError(message)
                callback.onFinished()
            }
        }
    }

    private fun finishRecording(text: String): java.io.File? {
        val writer = wavWriter
        wavWriter = null
        return runCatching { writer?.finish(text) }
            .onFailure { AppLog.add(context, "Failed to finish recording: ${it.message.orEmpty()}") }
            .getOrNull()
    }

    private fun discardRecording() {
        val writer = wavWriter
        wavWriter = null
        runCatching { writer?.discard() }
    }

    private fun cleanup() {
        connectTimeout?.cancel()
        finalTimeout?.cancel()
        runCatching { capture?.stop() }
        capture = null
        socket?.close()
        socket = null
        main.postDelayed({ scope.cancel() }, 1_000)
    }

    private fun postState(state: VoiceState, message: String) {
        main.post {
            if (!completed.get()) callback.onState(state, message)
        }
    }

    private companion object {
        const val FINAL_TIMEOUT_MS = 30_000L
    }
}
