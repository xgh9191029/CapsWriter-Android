package com.capswriter.offline.util

import android.content.Context
import android.content.Intent

enum class VoiceState {
    IDLE,
    CONNECTING,
    RECORDING,
    PROCESSING,
    ERROR,
}

/**
 * Small process-wide status bus. Both services live in the default process, so a
 * volatile snapshot plus a package-scoped broadcast is enough and avoids binding.
 */
object AppStatus {

    const val ACTION_STATUS = "com.capswriter.offline.action.STATUS_CHANGED"
    const val EXTRA_STATE = "state"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_LAST_RESULT = "last_result"
    const val EXTRA_SERVICE_RUNNING = "service_running"

    @Volatile
    var state: VoiceState = VoiceState.IDLE
        private set

    @Volatile
    var message: String = ""
        private set

    @Volatile
    var lastResult: String = ""
        private set

    @Volatile
    var serviceRunning: Boolean = false
        private set

    fun update(
        context: Context,
        state: VoiceState = this.state,
        message: String = this.message,
        lastResult: String = this.lastResult,
        serviceRunning: Boolean = this.serviceRunning,
        notify: Boolean = true,
    ) {
        this.state = state
        this.message = message
        this.lastResult = lastResult
        this.serviceRunning = serviceRunning
        if (notify) broadcast(context)
    }

    fun broadcast(context: Context) {
        val intent = Intent(ACTION_STATUS)
            .setPackage(context.packageName)
            .putExtra(EXTRA_STATE, state.name)
            .putExtra(EXTRA_MESSAGE, message)
            .putExtra(EXTRA_LAST_RESULT, lastResult)
            .putExtra(EXTRA_SERVICE_RUNNING, serviceRunning)
        context.sendBroadcast(intent)
    }
}
