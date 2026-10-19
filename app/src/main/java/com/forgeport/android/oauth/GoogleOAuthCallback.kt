package com.forgeport.android.oauth

import java.io.IOException
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

internal fun awaitGoogleOAuthCallback(server: ServerSocket, state: String, timeoutMillis: Long = 180_000): Map<String, String> {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while (true) {
        val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
        check(remaining > 0) { "Google sign-in timed out. Return to AetherPort and try again." }
        server.soTimeout = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
        val socket = try { server.accept() } catch (_: java.net.SocketTimeoutException) {
            error("Google sign-in timed out. Return to AetherPort and try again.")
        }
        val callback = socket.use {
            it.soTimeout = minOf(5_000, server.soTimeout)
            try {
                val reader = it.getInputStream().bufferedReader(Charsets.UTF_8)
                val line = buildString {
                    repeat(8192) {
                        val ch = reader.read()
                        if (ch == -1 || ch == '\n'.code) return@buildString
                        append(ch.toChar())
                    }
                }
                val path = line.split(' ').getOrNull(1).orEmpty()
                val query = runCatching { path.substringAfter('?', "").split('&').filter { it.isNotBlank() }.associate { part ->
                        URLDecoder.decode(part.substringBefore('='), "UTF-8") to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
                    } }.getOrDefault(emptyMap())
                val valid = line.startsWith("GET ") && path.substringBefore('?') == "/" &&
                    query["state"] == state && (query["code"].orEmpty().isNotBlank() || query.containsKey("error"))
                val message = if (valid) "Authorization received. You can return to AetherPort."
                    else "This is not a valid AetherPort authorization callback."
                val html = "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><title>AetherPort</title><p>$message</p>"
                val body = html.toByteArray(Charsets.UTF_8)
                it.getOutputStream().apply {
                    write(("HTTP/1.1 ${if (valid) "200 OK" else "400 Bad Request"}\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\n" +
                        "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                    write(body); flush()
                }
                if (valid) query else null
            } catch (_: IOException) { null }
        }
        if (callback != null) return callback
    }
}

