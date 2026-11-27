package com.capswriter.offline.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.capswriter.offline.R
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean

/** Checks that the server accepts a WebSocket with the CapsWriter subprotocol. */
object ServerProbe {

    fun test(context: Context, url: String, callback: (Boolean, String) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val done = AtomicBoolean(false)
        var socket: WebSocket? = null

        fun finish(ok: Boolean, message: String) {
            if (!done.compareAndSet(false, true)) return
            main.post { callback(ok, message) }
            if (!ok) runCatching { socket?.cancel() }
        }

        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", "binary")
            .build()

        socket = NetClient.client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                finish(true, "HTTP ${response.code}")
                webSocket.close(1000, "probe")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val detail = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                finish(false, detail)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!done.get()) {
                    finish(false, context.getString(R.string.server_closed, code))
                }
            }
        })

        main.postDelayed({
            if (done.compareAndSet(false, true)) {
                runCatching { socket?.cancel() }
                callback(false, context.getString(R.string.server_timeout))
            }
        }, 7_000)
    }
}
