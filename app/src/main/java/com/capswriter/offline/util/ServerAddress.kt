package com.capswriter.offline.util

import java.net.URI

/**
 * Turns the text a user types into a WebSocket URL.
 *
 * CapsWriter-Offline listens on ws://host:6016 by default. People naturally type
 * "192.168.1.8" or "192.168.1.8:6016", so accept those forms as well as a full URL.
 */
object ServerAddress {

    const val DEFAULT_PORT = 6016

    fun normalize(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "empty" }

        val withScheme = if (trimmed.contains("://")) trimmed else "ws://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (error: Exception) {
            throw IllegalArgumentException("invalid", error)
        }

        val scheme = when (uri.scheme?.lowercase()) {
            "ws", "http" -> "ws"
            "wss", "https" -> "wss"
            else -> throw IllegalArgumentException("scheme")
        }

        require(uri.userInfo == null) { "userinfo" }
        require(uri.query == null && uri.fragment == null) { "query" }

        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("host")
        val port = if (uri.port == -1) DEFAULT_PORT else uri.port
        require(port in 1..65535) { "port" }

        val path = uri.path?.takeIf { it.isNotBlank() && it != "/" }
        return URI(scheme, null, host, port, path, null, null).toString()
    }

    fun isValid(input: String): Boolean = runCatching { normalize(input) }.isSuccess
}
