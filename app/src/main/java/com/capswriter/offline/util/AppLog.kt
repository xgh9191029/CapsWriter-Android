package com.capswriter.offline.util

import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Bounded in-memory log shown at the bottom of the main screen. */
object AppLog {

    const val ACTION_LOG = "com.capswriter.offline.action.LOG_CHANGED"
    private const val TAG = "CapsWriter"
    private const val MAX_LINES = 40

    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    @Synchronized
    fun add(context: Context?, message: String) {
        val cleaned = message.replace('\n', ' ').take(240)
        val line = "${timeFormat.format(Date())}  $cleaned"
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        Log.i(TAG, cleaned)
        if (context != null) {
            context.sendBroadcast(Intent(ACTION_LOG).setPackage(context.packageName))
        }
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() = lines.clear()
}
