package com.forgeport.android

import com.forgeport.android.oauth.awaitGoogleOAuthCallback
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GoogleOAuthCallbackTest {
    private fun request(port: Int, path: String): String = Socket("127.0.0.1", port).use {
        it.soTimeout = 2_000
        it.getOutputStream().write("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
        it.getInputStream().bufferedReader().readText()
    }

    @Test fun invalidAndUnrelatedRequestsDoNotConsumeTheRealCallback() {
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            val worker = Executors.newSingleThreadExecutor()
            try {
                val pending = worker.submit<Map<String, String>> { awaitGoogleOAuthCallback(server, "expected", 5_000) }
                assertTrue(request(server.localPort, "/favicon.ico").startsWith("HTTP/1.1 400"))
                assertTrue(request(server.localPort, "/?state=wrong&code=attacker").startsWith("HTTP/1.1 400"))
                assertFalse(pending.isDone)
                assertTrue(request(server.localPort, "/?state=expected&code=code%2Bvalue").startsWith("HTTP/1.1 200"))
                assertEquals("code+value", pending.get(2, TimeUnit.SECONDS)["code"])
            } finally { worker.shutdownNow() }
        }
    }

    @Test fun deniedConsentReturnsOnlyWithMatchingState() {
        ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { server ->
            val worker = Executors.newSingleThreadExecutor()
            try {
                val pending = worker.submit<Map<String, String>> { awaitGoogleOAuthCallback(server, "expected", 5_000) }
                assertTrue(request(server.localPort, "/?state=wrong&error=access_denied").startsWith("HTTP/1.1 400"))
                request(server.localPort, "/?state=expected&error=access_denied")
                assertEquals("access_denied", pending.get(2, TimeUnit.SECONDS)["error"])
            } finally { worker.shutdownNow() }
        }
    }

    @Test fun missingCallbackTimesOut() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            try {
                awaitGoogleOAuthCallback(server, "expected", 50)
                fail("Expected a timeout")
            } catch (failure: IllegalStateException) {
                assertTrue(failure.message.orEmpty().contains("timed out"))
            }
        }
    }
}
