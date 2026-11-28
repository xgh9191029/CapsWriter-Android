package com.capswriter.offline.net

import android.os.Handler
import android.os.Looper
import com.capswriter.offline.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Checks the latest GitHub release for a newer version. The call is silent on
 * failure (offline, no releases, rate limit), so startup never blocks.
 */
object UpdateChecker {

    data class Update(
        val version: String,
        val pageUrl: String,
    )

    private val main = Handler(Looper.getMainLooper())

    fun check(callback: (Update?) -> Unit) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CapsWriter-Android/${BuildConfig.VERSION_NAME}")
            .build()
        val client = NetClient.client.newBuilder()
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                main.post { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val update = response.use {
                    if (!it.isSuccessful) return@use null
                    val body = it.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    runCatching {
                        val json = JSONObject(body)
                        val tag = json.optString("tag_name").trim().removePrefix("v")
                        if (tag.isBlank()) return@use null
                        val page = json.optString("html_url").ifBlank {
                            "https://github.com/${BuildConfig.GITHUB_REPO}/releases"
                        }
                        Update(tag, page)
                    }.getOrNull()
                }
                main.post { callback(update) }
            }
        })
    }

    fun isNewer(latest: String, current: String): Boolean {
        val left = versionParts(latest)
        val right = versionParts(current)
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull() }
}
