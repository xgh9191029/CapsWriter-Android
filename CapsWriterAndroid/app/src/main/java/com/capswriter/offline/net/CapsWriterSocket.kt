package com.capswriter.offline.net

import android.util.Base64
import android.util.Log
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * One WebSocket connection used by one dictation task.
 *
 * The CapsWriter-Offline protocol is JSON over a WebSocket whose subprotocol is
 * "binary". Each message contains base64 encoded little-endian float32 mono PCM
 * at 16 kHz. The final packet has an empty `data` field and `is_final=true`.
 */
class CapsWriterSocket(
    private val url: String,
    private val taskId: String,
    private val hotwordContext: String,
    private val language: String,
    private val listener: Listener,
) {

    interface Listener {
        fun onOpen()
        fun onFinalText(text: String)
        fun onFailure(message: String)
        fun onClosed()
    }

    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private var socket: WebSocket? = null
    private var opened = false
    private var closed = false
    private var finalSeen = false

    fun connect() {
        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", "binary")
            .build()
        socket = NetClient.client.newWebSocket(request, socketListener)
    }

    fun sendAudio(data: ByteArray, isFinal: Boolean, startTime: Double) {
        val message = JSONObject()
            .put("task_id", taskId)
            .put("source", "mic")
            .put("data", if (isFinal) "" else Base64.encodeToString(data, Base64.NO_WRAP))
            .put("is_final", isFinal)
            .put("time_start", startTime)
            .put("seg_duration", 60.0)
            .put("seg_overlap", 4.0)
            .put("context", hotwordContext)
            .put("language", language)
            .toString()

        var sentNow = false
        synchronized(lock) {
            if (opened && !closed) {
                sentNow = socket?.send(message) == true
            }
            if (!sentNow && !closed && pending.size < 500) {
                pending.addLast(message)
            }
        }
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            pending.clear()
            socket?.close(1000, "done")
        }
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (closed) {
                    webSocket.close(1000, "cancelled")
                    return
                }
                opened = true
                while (pending.isNotEmpty()) {
                    webSocket.send(pending.removeFirst())
                }
            }
            listener.onOpen()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = try {
                JSONObject(text)
            } catch (error: Exception) {
                Log.w(TAG, "Ignoring non-JSON server message", error)
                return
            }

            val task = json.optString("task_id")
            if (task.isNotBlank() && task != taskId) return

            val isFinal = json.optBoolean("is_final", false)
            if (!isFinal) return

            synchronized(lock) {
                if (finalSeen) return
                finalSeen = true
            }
            listener.onFinalText(json.optString("text", ""))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val wasFinal = synchronized(lock) { finalSeen || closed }
            if (!wasFinal) {
                val detail = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                val http = response?.code?.let { " (HTTP $it)" }.orEmpty()
                listener.onFailure("$detail$http")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            val wasFinal = synchronized(lock) { finalSeen || closed }
            if (!wasFinal) listener.onClosed()
        }
    }

    private companion object {
        const val TAG = "CapsWriterSocket"
    }
}
