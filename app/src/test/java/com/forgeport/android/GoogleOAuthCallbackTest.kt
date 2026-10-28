package com.forgeport.android

import com.forgeport.android.oauth.awaitGoogleOAuthCallback
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
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

    @Test fun idleBrowserPreconnectionDoesNotBlockTheRealCallback() {
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            val worker = Executors.newSingleThreadExecutor()
            try {
                val pending = worker.submit<Map<String, String>> { awaitGoogleOAuthCallback(server, "expected", 5_000) }
                Socket("127.0.0.1", server.localPort).use {
                    assertTrue(request(server.localPort, "/?state=expected&code=real").startsWith("HTTP/1.1 200"))
                    assertEquals("real", pending.get(2, TimeUnit.SECONDS)["code"])
                }
            } finally { worker.shutdownNow() }
        }
    }

    @Test fun browserReceivesCompleteResponseAfterAllHeadersAreRead() {
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            val worker = Executors.newSingleThreadExecutor()
            try {
                val pending = worker.submit<Map<String, String>> { awaitGoogleOAuthCallback(server, "expected", 5_000) }
                Socket("127.0.0.1", server.localPort).use { browser ->
                    browser.soTimeout = 2_000
                    browser.getOutputStream().write(("GET /?state=expected&code=real HTTP/1.1\r\n" +
                        "Host: 127.0.0.1\r\nUser-Agent: ${"x".repeat(12_000)}\r\n\r\n").toByteArray())
                    val response = browser.getInputStream().bufferedReader().readText()
                    val body = response.substringAfter("\r\n\r\n")
                    assertTrue(response.startsWith("HTTP/1.1 200"))
                    assertTrue(response.contains("Content-Length: ${body.toByteArray().size}\r\n"))
                    assertTrue(body.contains("Authorization received"))
                }
                assertEquals("real", pending.get(2, TimeUnit.SECONDS)["code"])
            } finally { worker.shutdownNow() }
        }
    }

    @Test fun incompleteHttpRequestCannotExtendTheOverallDeadline() {
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            val worker = Executors.newSingleThreadExecutor()
            try {
                val pending = worker.submit<Map<String, String>> { awaitGoogleOAuthCallback(server, "expected", 200) }
                Socket("127.0.0.1", server.localPort).use { browser ->
                    browser.getOutputStream().write("GET /?state=expected&code=real HTTP/1.1\r\nHost: ".toByteArray())
                    try {
                        pending.get(2, TimeUnit.SECONDS)
                        fail("Expected the overall timeout despite the open connection")
                    } catch (failure: ExecutionException) {
                        assertTrue(failure.cause is IllegalStateException)
                        assertTrue(failure.cause?.message.orEmpty().contains("timed out"))
                    }
                }
            } finally { worker.shutdownNow() }
        }
    }
}
