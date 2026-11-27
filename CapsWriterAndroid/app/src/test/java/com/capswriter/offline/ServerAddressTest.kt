package com.capswriter.offline

import com.capswriter.offline.util.ServerAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerAddressTest {

    @Test
    fun addsSchemeAndDefaultPort() {
        assertEquals("ws://192.168.1.8:6016", ServerAddress.normalize("192.168.1.8"))
    }

    @Test
    fun keepsExplicitPort() {
        assertEquals("ws://192.168.1.8:6006", ServerAddress.normalize("192.168.1.8:6006"))
    }

    @Test
    fun convertsHttpToWebSocket() {
        assertEquals("ws://192.168.1.8:6016", ServerAddress.normalize("http://192.168.1.8:6016"))
        assertEquals("wss://example.com:6016", ServerAddress.normalize("https://example.com"))
    }

    @Test
    fun keepsPath() {
        assertEquals("ws://example.com:6016/caps", ServerAddress.normalize("ws://example.com/caps"))
    }

    @Test
    fun supportsIpv6() {
        assertEquals("ws://[::1]:6016", ServerAddress.normalize("[::1]"))
    }

    @Test
    fun rejectsInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) { ServerAddress.normalize("") }
        assertThrows(IllegalArgumentException::class.java) { ServerAddress.normalize("ftp://host") }
        assertThrows(IllegalArgumentException::class.java) { ServerAddress.normalize("ws://") }
    }

    @Test
    fun validationMatchesNormalization() {
        assertFalse(ServerAddress.isValid(""))
        assertEquals(true, ServerAddress.isValid("10.0.0.2:6016"))
    }
}
